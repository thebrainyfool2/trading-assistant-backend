package com.tradingassistant.monitoring.engine;

import com.tradingassistant.intelligence.domain.StructureBias;
import com.tradingassistant.intelligence.domain.TrendState;
import com.tradingassistant.monitoring.config.MonitoringConfig;
import com.tradingassistant.monitoring.domain.ComponentChange;
import com.tradingassistant.monitoring.domain.ExitRecommendation;
import com.tradingassistant.monitoring.domain.ManualTrade;
import com.tradingassistant.monitoring.domain.MonitoringAlertType;
import com.tradingassistant.monitoring.domain.MonitoringState;
import com.tradingassistant.monitoring.domain.MonitoringWarning;
import com.tradingassistant.monitoring.domain.TradeMonitoringContext;
import com.tradingassistant.monitoring.domain.TradeMonitoringResult;
import com.tradingassistant.monitoring.validation.MonitoringValidator;
import com.tradingassistant.platform.readmodel.MarketDataFreshnessService.MarketSessionState;
import com.tradingassistant.recommendation.domain.ComponentScore;
import com.tradingassistant.recommendation.domain.Recommendation;
import com.tradingassistant.recommendation.domain.RecommendationType;
import com.tradingassistant.recommendation.domain.ScorerId;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Pure deterministic rescoring of one active manual trade.
 *
 * <p>The engine consumes the Phase 14 recommendation and its component scores.
 * It never calls an indicator, option analytic, risk or recommendation producer,
 * never reads a clock and never produces an execution instruction.</p>
 */
public final class TradeMonitoringEngine {

    private static final Set<ScorerId> IMPORTANT = Set.of(
            ScorerId.TREND, ScorerId.MOMENTUM, ScorerId.LIQUIDITY,
            ScorerId.OPEN_INTEREST, ScorerId.PCR, ScorerId.GREEKS,
            ScorerId.MARKET_STRUCTURE, ScorerId.TREND_ALIGNMENT,
            ScorerId.RISK_REWARD, ScorerId.QUALIFICATION,
            ScorerId.STRATEGY_AGREEMENT, ScorerId.FRESHNESS);

    private final MonitoringConfig config;
    private final MonitoringValidator validator;

    public TradeMonitoringEngine(MonitoringConfig config) {
        this(config, new MonitoringValidator(config));
    }

    public TradeMonitoringEngine(MonitoringConfig config, MonitoringValidator validator) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
    }

    public TradeMonitoringResult evaluate(TradeMonitoringContext input) {
        TradeMonitoringContext context = validator.align(input);
        ManualTrade trade = context.trade();
        Recommendation recommendation = context.recommendation();
        BigDecimal currentConfidence = recommendation.confidence();
        BigDecimal currentEdge = alignedEdge(recommendation, trade);
        Optional<TradeMonitoringResult> previous = context.previousResult();
        BigDecimal previousConfidence = previous
                .map(TradeMonitoringResult::currentConfidence).orElse(currentConfidence);
        BigDecimal previousEdge = previous
                .map(TradeMonitoringResult::currentDirectionalEdge).orElse(currentEdge);
        BigDecimal confidenceDelta = currentConfidence.subtract(previousConfidence);
        BigDecimal edgeDelta = currentEdge.subtract(previousEdge);
        List<ComponentChange> changes = changes(recommendation.components(), previous);
        Map<ScorerId, BigDecimal> scores = scores(recommendation.components());

        List<MonitoringWarning> warnings = new ArrayList<>();
        boolean sessionClosed = context.freshness().session() == MarketSessionState.CLOSED;
        boolean typeLost = recommendation.type() == RecommendationType.NO_TRADE
                || recommendation.type() != trade.recommendationType();
        boolean edgeReversed = currentEdge.signum() <= 0;
        boolean trendAgainst = trendAgainst(context.market().trend().state(), trade);
        boolean structureAgainst = structureAgainst(context.market().structure().bias(), trade);
        int importantDeteriorated = (int) changes.stream()
                .filter(change -> IMPORTANT.contains(change.scorerId()))
                .filter(change -> change.delta().compareTo(
                        config.componentDeterioration().negate()) <= 0)
                .count();

        if (sessionClosed) {
            warnings.add(warning(MonitoringAlertType.SESSION_CLOSED,
                    "market session is closed; monitoring is using the last completed evidence",
                    Map.of("session", context.freshness().session().name())));
        }
        if (typeLost && !sessionClosed) {
            warnings.add(warning(MonitoringAlertType.THESIS_INVALIDATED,
                    "current recommendation is " + recommendation.type().name()
                            + "; the original " + trade.recommendationType().name()
                            + " thesis is no longer supported",
                    Map.of("currentType", recommendation.type().name(),
                            "tradeType", trade.recommendationType().name())));
        }
        if (!context.qualification().qualified()) {
            warnings.add(warning(MonitoringAlertType.QUALIFICATION_LOST,
                    "trade qualification is no longer QUALIFIED: "
                            + context.qualification().status().name(),
                    Map.of("status", context.qualification().status().name())));
        }
        if (!context.risk().withinLimits()) {
            warnings.add(warning(MonitoringAlertType.RISK_DETERIORATION,
                    "risk assessment is no longer within limits: "
                            + context.risk().status().name(),
                    Map.of("status", context.risk().status().name())));
        }
        if (previous.isPresent()
                && confidenceDelta.compareTo(config.confidenceDeterioration().negate()) <= 0) {
            warnings.add(warning(MonitoringAlertType.CONFIDENCE_DETERIORATION,
                    "confidence deteriorated by " + confidenceDelta.abs().toPlainString()
                            + " percentage points",
                    Map.of("previousConfidence", previousConfidence.toPlainString(),
                            "currentConfidence", currentConfidence.toPlainString())));
        }
        if (previous.isPresent()
                && edgeDelta.compareTo(config.edgeDeterioration().negate()) <= 0) {
            warnings.add(warning(MonitoringAlertType.DIRECTIONAL_EDGE_DETERIORATION,
                    "trade-aligned directional edge deteriorated by "
                            + edgeDelta.abs().toPlainString(),
                    Map.of("previousEdge", previousEdge.toPlainString(),
                            "currentEdge", currentEdge.toPlainString())));
        }
        if (trendAgainst) {
            warnings.add(warning(MonitoringAlertType.TREND_REVERSAL,
                    "market trend " + context.market().trend().state().name()
                            + " opposes the trade direction",
                    Map.of("trend", context.market().trend().state().name())));
        }
        if (structureAgainst) {
            warnings.add(warning(MonitoringAlertType.STRUCTURE_REVERSAL,
                    "market structure " + context.market().structure().bias().name()
                            + " opposes the trade direction",
                    Map.of("structure", context.market().structure().bias().name())));
        }
        changes.stream()
                .filter(change -> change.scorerId() == ScorerId.LIQUIDITY)
                .filter(change -> change.delta().compareTo(
                        config.componentDeterioration().negate()) <= 0)
                .findFirst()
                .ifPresent(change -> warnings.add(warning(
                        MonitoringAlertType.LIQUIDITY_DETERIORATION,
                        "liquidity score deteriorated from "
                                + change.previousScore().toPlainString() + " to "
                                + change.currentScore().toPlainString(),
                        Map.of("delta", change.delta().toPlainString()))));
        componentScore(recommendation, ScorerId.STRATEGY_AGREEMENT)
                .filter(score -> score.compareTo(config.strategyDisagreementScore()) < 0)
                .ifPresent(score -> warnings.add(warning(
                        MonitoringAlertType.STRATEGY_DISAGREEMENT,
                        "strategy agreement score " + score.toPlainString()
                                + " is below the configured agreement floor",
                        Map.of("score", score.toPlainString()))));
        if (importantDeteriorated >= config.atRiskComponentCount()) {
            warnings.add(warning(MonitoringAlertType.COMPONENT_DETERIORATION,
                    importantDeteriorated + " important components deteriorated by at least "
                            + config.componentDeterioration().toPlainString() + " points",
                    Map.of("count", Integer.toString(importantDeteriorated))));
        }

        MonitoringState state = state(context, typeLost, edgeReversed, trendAgainst,
                structureAgainst, importantDeteriorated, confidenceDelta, edgeDelta,
                currentConfidence, sessionClosed);
        ExitRecommendation exit = exitRecommendation(state);
        String explanation = explanation(state, exit, currentConfidence, confidenceDelta,
                currentEdge, edgeDelta, importantDeteriorated, warnings);
        Map<String, String> diagnostics = diagnostics(context, recommendation,
                previousConfidence, currentConfidence, previousEdge, currentEdge,
                confidenceDelta, edgeDelta, importantDeteriorated, warnings);
        return new TradeMonitoringResult(trade.tradeId(), context.market().timestamp(),
                state, exit, currentConfidence, currentEdge, confidenceDelta, edgeDelta,
                changes, scores, warnings, explanation, diagnostics);
    }

    private MonitoringState state(TradeMonitoringContext context, boolean typeLost,
                                  boolean edgeReversed, boolean trendAgainst,
                                  boolean structureAgainst, int importantDeteriorated,
                                  BigDecimal confidenceDelta, BigDecimal edgeDelta,
                                  BigDecimal currentConfidence, boolean sessionClosed) {
        if (!context.risk().withinLimits() || !context.qualification().qualified()
                || currentConfidence.compareTo(config.exitConfidence()) <= 0) {
            return MonitoringState.EXIT_RECOMMENDED;
        }
        if (!sessionClosed && (typeLost || edgeReversed
                || (trendAgainst && structureAgainst))) {
            return MonitoringState.INVALIDATED;
        }
        boolean deterioration = confidenceDelta.compareTo(
                config.confidenceDeterioration().negate()) <= 0
                || edgeDelta.compareTo(config.edgeDeterioration().negate()) <= 0;
        if (!sessionClosed && importantDeteriorated >= config.atRiskComponentCount()) {
            return MonitoringState.AT_RISK;
        }
        if (!sessionClosed && importantDeteriorated >= config.warningComponentCount()
                && deterioration) {
            return MonitoringState.AT_RISK;
        }
        if (sessionClosed || currentConfidence.compareTo(config.warningConfidence()) < 0
                || importantDeteriorated >= config.warningComponentCount() || deterioration
                || trendAgainst || structureAgainst) {
            return MonitoringState.WEAKENING;
        }
        return MonitoringState.HEALTHY;
    }

    private static ExitRecommendation exitRecommendation(MonitoringState state) {
        return switch (state) {
            case HEALTHY -> ExitRecommendation.HOLD;
            case WEAKENING -> ExitRecommendation.WATCH_CLOSELY;
            case AT_RISK -> ExitRecommendation.PREPARE_EXIT;
            case EXIT_RECOMMENDED, INVALIDATED -> ExitRecommendation.EXIT_NOW;
        };
    }

    private static List<ComponentChange> changes(List<ComponentScore> current,
                                                 Optional<TradeMonitoringResult> previous) {
        if (previous.isEmpty() || previous.get().componentScores().isEmpty()) {
            return List.of();
        }
        Map<ScorerId, BigDecimal> before = previous.get().componentScores();
        List<ComponentChange> changes = new ArrayList<>();
        for (ComponentScore score : current) {
            BigDecimal old = before.get(score.scorerId());
            if (old == null) {
                continue;
            }
            BigDecimal delta = score.score().subtract(old);
            changes.add(new ComponentChange(score.scorerId(), old, score.score(), delta,
                    score.scorerId().label() + " changed from " + old.toPlainString()
                            + " to " + score.score().toPlainString()));
        }
        return List.copyOf(changes);
    }

    private static Map<ScorerId, BigDecimal> scores(List<ComponentScore> components) {
        Map<ScorerId, BigDecimal> values = new EnumMap<>(ScorerId.class);
        for (ComponentScore component : components) {
            values.put(component.scorerId(), component.score());
        }
        return Map.copyOf(values);
    }

    private static BigDecimal alignedEdge(Recommendation recommendation,
                                          ManualTrade trade) {
        BigDecimal signed = recommendation.directionalEdge();
        if (trade.recommendationType() == RecommendationType.BUY_PUT) {
            return signed.negate();
        }
        return signed;
    }

    private static boolean trendAgainst(TrendState trend, ManualTrade trade) {
        return trade.recommendationType() == RecommendationType.BUY_CALL
                ? trend == TrendState.BEARISH : trend == TrendState.BULLISH;
    }

    private static boolean structureAgainst(StructureBias structure, ManualTrade trade) {
        return trade.recommendationType() == RecommendationType.BUY_CALL
                ? structure == StructureBias.DOWNTREND
                : structure == StructureBias.UPTREND;
    }

    private static Optional<BigDecimal> componentScore(Recommendation recommendation,
                                                       ScorerId id) {
        return recommendation.component(id).map(ComponentScore::score);
    }

    private static MonitoringWarning warning(MonitoringAlertType type, String message,
                                             Map<String, String> diagnostics) {
        return new MonitoringWarning(type, message, diagnostics);
    }

    private static String explanation(MonitoringState state, ExitRecommendation exit,
                                      BigDecimal confidence, BigDecimal confidenceDelta,
                                      BigDecimal edge, BigDecimal edgeDelta,
                                      int importantDeteriorated,
                                      List<MonitoringWarning> warnings) {
        StringBuilder text = new StringBuilder();
        text.append(state.name()).append(" / ").append(exit.name())
                .append(": confidence ").append(confidence.toPlainString())
                .append("% (").append(signed(confidenceDelta)).append(") ")
                .append("and trade-aligned edge ").append(edge.toPlainString())
                .append(" (").append(signed(edgeDelta)).append(")");
        if (importantDeteriorated > 0) {
            text.append("; ").append(importantDeteriorated)
                    .append(" important components deteriorated");
        }
        if (!warnings.isEmpty()) {
            text.append("; ").append(warnings.size()).append(" warning(s): ")
                    .append(warnings.stream().map(MonitoringWarning::message)
                            .reduce((left, right) -> left + "; " + right).orElse(""));
        } else {
            text.append("; no deterioration warnings");
        }
        return text.toString();
    }

    private static String signed(BigDecimal value) {
        return value.signum() > 0 ? "+" + value.toPlainString() : value.toPlainString();
    }

    private static Map<String, String> diagnostics(TradeMonitoringContext context,
                                                   Recommendation recommendation,
                                                   BigDecimal previousConfidence,
                                                   BigDecimal currentConfidence,
                                                   BigDecimal previousEdge,
                                                   BigDecimal currentEdge,
                                                   BigDecimal confidenceDelta,
                                                   BigDecimal edgeDelta,
                                                   int importantDeteriorated,
                                                   List<MonitoringWarning> warnings) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("tradeId", context.trade().tradeId());
        values.put("tradeType", context.trade().recommendationType().name());
        values.put("recommendationType", recommendation.type().name());
        values.put("recommendationTimestamp", recommendation.timestamp().toString());
        values.put("previousConfidence", previousConfidence.toPlainString());
        values.put("currentConfidence", currentConfidence.toPlainString());
        values.put("confidenceDelta", confidenceDelta.toPlainString());
        values.put("previousDirectionalEdge", previousEdge.toPlainString());
        values.put("currentDirectionalEdge", currentEdge.toPlainString());
        values.put("directionalEdgeDelta", edgeDelta.toPlainString());
        values.put("importantDeteriorated", Integer.toString(importantDeteriorated));
        values.put("warningCount", Integer.toString(warnings.size()));
        values.put("weightedScore", recommendation.weightedScore().toPlainString());
        values.put("phase14DecisionReason",
                recommendation.diagnostics().getOrDefault("decisionReason", ""));
        return values;
    }
}