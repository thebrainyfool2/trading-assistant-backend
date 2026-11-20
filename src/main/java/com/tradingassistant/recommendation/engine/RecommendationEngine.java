package com.tradingassistant.recommendation.engine;

import com.tradingassistant.platform.readmodel.MarketDataFreshnessService.MarketSessionState;
import com.tradingassistant.qualification.domain.QualificationStatus;
import com.tradingassistant.qualification.domain.TradeQualification;
import com.tradingassistant.recommendation.api.RecommendationScorer;
import com.tradingassistant.recommendation.config.RecommendationConfig;
import com.tradingassistant.recommendation.domain.ComponentScore;
import com.tradingassistant.recommendation.domain.DirectionalBias;
import com.tradingassistant.recommendation.domain.OptionSelection;
import com.tradingassistant.recommendation.domain.Recommendation;
import com.tradingassistant.recommendation.domain.RecommendationBatch;
import com.tradingassistant.recommendation.domain.RecommendationContext;
import com.tradingassistant.recommendation.domain.RecommendationType;
import com.tradingassistant.recommendation.domain.ScorerResult;
import com.tradingassistant.recommendation.registry.ScorerRegistry;
import com.tradingassistant.recommendation.validation.RecommendationValidator;
import com.tradingassistant.risk.domain.RiskAssessment;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The pure recommendation engine. It combines immutable outputs from existing
 * engines, applies configured weights and gates, and returns an explainable
 * {@link Recommendation}. There is no clock, no randomness, no I/O and no
 * execution path.
 */
public final class RecommendationEngine {

    private final ScorerRegistry registry;
    private final RecommendationConfig config;
    private final OptionSelector optionSelector;
    private final RecommendationValidator validator;

    public RecommendationEngine(ScorerRegistry registry, RecommendationConfig config) {
        this(registry, config, new OptionSelector(), new RecommendationValidator());
    }

    public RecommendationEngine(ScorerRegistry registry, RecommendationConfig config,
                                OptionSelector optionSelector,
                                RecommendationValidator validator) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.optionSelector = Objects.requireNonNull(optionSelector,
                "optionSelector must not be null");
        this.validator = Objects.requireNonNull(validator,
                "validator must not be null");
    }

    public Recommendation evaluate(RecommendationContext input) {
        RecommendationContext context = validator.align(input);
        List<ComponentScore> components = registry.scorers().stream()
                .map(scorer -> score(scorer, context))
                .toList();
        BigDecimal weightedScore = components.stream()
                .map(ComponentScore::weightedContribution)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal confidence = weightedScore
                .divide(config.totalWeight(), MathContext.DECIMAL128)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        DirectionEdge edge = directionEdge(components);
        Gate gate = gate(context, components, edge, confidence);
        Optional<OptionSelection> option = gate.type() == RecommendationType.NO_TRADE
                ? Optional.empty()
                : optionSelector.select(gate.type(), context, config);
        RecommendationType type = gate.type();
        String reason = gate.reason();
        if (type != RecommendationType.NO_TRADE && option.isEmpty()) {
            type = RecommendationType.NO_TRADE;
            reason = "no supported option contract could be selected";
        }

        Map<String, String> diagnostics = new LinkedHashMap<>();
        diagnostics.put("weightedScore", weightedScore.setScale(4, RoundingMode.HALF_UP)
                .toPlainString());
        diagnostics.put("totalWeight", config.totalWeight().toPlainString());
        diagnostics.put("confidence", confidence.toPlainString());
        diagnostics.put("bullishWeight", edge.bullish().toPlainString());
        diagnostics.put("bearishWeight", edge.bearish().toPlainString());
        diagnostics.put("directionEdge", edge.edge().toPlainString());
        diagnostics.put("minimumDirectionalEdge",
                config.minimumDirectionalEdge().toPlainString());
        diagnostics.put("minimumConfidence", config.minimumConfidence().toPlainString());
        diagnostics.put("decisionReason", reason);
        diagnostics.put("interval", context.market().interval().name());
        diagnostics.put("instrument", context.market().instrument().symbolToken());
        diagnostics.put("timestamp", context.market().timestamp().toString());

        return new Recommendation(type, context.market().instrument(),
                context.market().interval(), context.market().timestamp(),
                weightedScore, confidence, components, option,
                summary(type, reason, components, confidence, weightedScore),
                diagnostics);
    }

    /**
     * Evaluates a set of contexts and exposes deterministic ranking.
     */
    public RecommendationBatch evaluateBatch(List<RecommendationContext> contexts) {
        Objects.requireNonNull(contexts, "contexts must not be null");
        return new RecommendationBatch(contexts.stream().map(this::evaluate).toList());
    }

    private ComponentScore score(RecommendationScorer scorer,
                                 RecommendationContext context) {
        ScorerResult result = scorer.score(context);
        return ComponentScore.from(result, config.weight(scorer.id()));
    }

    private DirectionEdge directionEdge(List<ComponentScore> components) {
        BigDecimal bullish = components.stream()
                .filter(component -> component.bias() == DirectionalBias.BULLISH)
                .map(ComponentScore::weightedContribution)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal bearish = components.stream()
                .filter(component -> component.bias() == DirectionalBias.BEARISH)
                .map(ComponentScore::weightedContribution)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DirectionEdge(bullish, bearish, bullish.subtract(bearish));
    }

    private Gate gate(RecommendationContext context,
                      List<ComponentScore> components,
                      DirectionEdge edge,
                      BigDecimal confidence) {
        TradeQualification qualification = context.qualifications().stream()
                .filter(value -> value.status() == QualificationStatus.QUALIFIED)
                .findFirst()
                .orElse(null);
        if (qualification == null) {
            return new Gate(RecommendationType.NO_TRADE,
                    "no QUALIFIED trade qualification is available");
        }
        RiskAssessment risk = context.risks().stream()
                .filter(RiskAssessment::withinLimits)
                .filter(value -> value.strategyId().equals(qualification.strategyId()))
                .findFirst()
                .orElseGet(() -> context.risks().stream()
                        .filter(RiskAssessment::withinLimits)
                        .findFirst().orElse(null));
        if (risk == null) {
            return new Gate(RecommendationType.NO_TRADE,
                    "no risk assessment within limits is available");
        }
        if (context.options().isEmpty() || context.chain().isEmpty()) {
            return new Gate(RecommendationType.NO_TRADE,
                    "options assessment or option chain is unavailable");
        }
        if (context.freshness().session() == MarketSessionState.CLOSED) {
            return new Gate(RecommendationType.NO_TRADE,
                    "market session is closed");
        }
        if (edge.edge().abs().compareTo(config.minimumDirectionalEdge()) < 0) {
            return new Gate(RecommendationType.NO_TRADE,
                    "weighted directional edge " + edge.edge().toPlainString()
                            + " is below minimum "
                            + config.minimumDirectionalEdge().toPlainString());
        }
        if (confidence.compareTo(config.minimumConfidence()) < 0) {
            return new Gate(RecommendationType.NO_TRADE,
                    "confidence " + confidence.toPlainString()
                            + " is below minimum "
                            + config.minimumConfidence().toPlainString());
        }
        return new Gate(edge.edge().signum() > 0
                ? RecommendationType.BUY_CALL : RecommendationType.BUY_PUT,
                "weighted directional evidence supports "
                        + (edge.edge().signum() > 0 ? "a call" : "a put"));
    }

    private String summary(RecommendationType type, String reason,
                           List<ComponentScore> components,
                           BigDecimal confidence, BigDecimal weightedScore) {
        String supporting = components.stream()
                .sorted((left, right) -> right.weightedContribution()
                        .compareTo(left.weightedContribution()))
                .limit(6)
                .map(component -> component.label() + " " + component.score()
                        + "/" + component.maximum())
                .collect(Collectors.joining(", "));
        return type.name().replace('_', ' ') + ": " + reason
                + ". Weighted score " + weightedScore.setScale(2, RoundingMode.HALF_UP)
                + "/" + config.totalWeight().setScale(2, RoundingMode.HALF_UP)
                + ", confidence " + confidence.toPlainString() + "%. "
                + "Leading components: " + supporting + ".";
    }

    private record DirectionEdge(BigDecimal bullish, BigDecimal bearish,
                                 BigDecimal edge) {
    }

    private record Gate(RecommendationType type, String reason) {
    }
}
