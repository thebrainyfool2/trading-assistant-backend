package com.tradingassistant.platform.trade;

import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.OptionType;
import com.tradingassistant.options.domain.ExpiryKind;
import com.tradingassistant.options.domain.Moneyness;
import com.tradingassistant.platform.instruments.InstrumentDtos;
import com.tradingassistant.platform.instruments.InstrumentReferences;
import com.tradingassistant.recommendation.domain.ComponentScore;
import com.tradingassistant.recommendation.domain.OptionSelection;
import com.tradingassistant.recommendation.domain.Recommendation;
import com.tradingassistant.recommendation.domain.RecommendationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Stable read-only projections for actionable recommendations. */
public final class RecommendationDtos {

    private RecommendationDtos() {
    }

    public record ComponentView(
            String scorerId,
            String label,
            BigDecimal score,
            BigDecimal maximum,
            BigDecimal weight,
            BigDecimal weightedContribution,
            String bias,
            String explanation,
            Map<String, String> diagnostics) {

        public static ComponentView from(ComponentScore score) {
            return new ComponentView(score.scorerId().id(), score.label(),
                    score.score(), score.maximum(), score.weight(),
                    score.weightedContribution(), score.bias().name(),
                    score.explanation(), score.diagnostics());
        }
    }

    public record OptionView(
            LocalDate expiry,
            String expiryKind,
            BigDecimal strike,
            OptionType optionType,
            String moneyness,
            String requestedMode,
            int offsetSteps) {

        public static OptionView from(OptionSelection selection) {
            return new OptionView(selection.expiry(), selection.expiryKind().name(),
                    selection.strike(), selection.optionType(),
                    selection.moneyness().name(), selection.requestedMode().name(),
                    selection.offsetSteps());
        }
    }

    public record PlanView(
            String optionInstrument,
            String optionSymbol,
            LocalDate expiry,
            BigDecimal strike,
            OptionType optionType,
            BigDecimal lastPremium,
            BigDecimal suggestedEntry,
            BigDecimal targetPremium,
            BigDecimal stopPremium,
            BigDecimal premiumTargetDistance,
            BigDecimal premiumStopDistance,
            BigDecimal underlyingTargetDistance,
            BigDecimal underlyingStopDistance,
            BigDecimal delta,
            long lotSize,
            BigDecimal suggestedQuantity,
            BigDecimal maximumLoss,
            BigDecimal potentialReward,
            BigDecimal rewardRiskRatio,
            String basis,
            Instant generatedAt,
            Map<String, String> diagnostics) {

        public static PlanView from(TradePlan plan) {
            return new PlanView(InstrumentReferences.format(plan.optionInstrument()),
                    plan.optionSymbol(), plan.expiry(), plan.strike(),
                    plan.optionType(), plan.lastPremium(), plan.suggestedEntry(),
                    plan.targetPremium(), plan.stopPremium(),
                    plan.premiumTargetDistance(), plan.premiumStopDistance(),
                    plan.underlyingTargetDistance(), plan.underlyingStopDistance(),
                    plan.delta(), plan.lotSize(), plan.suggestedQuantity(),
                    plan.maximumLoss(), plan.potentialReward(),
                    plan.rewardRiskRatio(), plan.basis(), plan.generatedAt(),
                    plan.diagnostics());
        }
    }

    public record RecommendationView(
            RecommendationType type,
            String instrument,
            Interval interval,
            Instant timestamp,
            BigDecimal weightedScore,
            BigDecimal confidence,
            BigDecimal directionalEdge,
            List<ComponentView> components,
            Optional<OptionView> option,
            String summary,
            Map<String, String> diagnostics,
            Optional<PlanView> plan) {

        public static RecommendationView from(Recommendation recommendation,
                                              Optional<TradePlan> plan) {
            return new RecommendationView(recommendation.type(),
                    InstrumentReferences.format(recommendation.instrument()),
                    recommendation.interval(), recommendation.timestamp(),
                    recommendation.weightedScore(), recommendation.confidence(),
                    recommendation.directionalEdge(),
                    recommendation.components().stream().map(ComponentView::from)
                            .toList(),
                    recommendation.option().map(OptionView::from),
                    recommendation.summary(), recommendation.diagnostics(),
                    plan.map(PlanView::from));
        }
    }

    public record RecommendationList(List<RecommendationView> recommendations) {
    }
}
