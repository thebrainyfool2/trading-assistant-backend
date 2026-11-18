package com.tradingassistant.strategy.rule;

import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.intelligence.domain.StructureSignal;
import com.tradingassistant.intelligence.domain.VolatilityState;
import com.tradingassistant.strategy.api.StrategyRule;
import com.tradingassistant.strategy.condition.ConditionSupport;
import com.tradingassistant.strategy.domain.RuleEvaluation;
import com.tradingassistant.strategy.domain.StrategyEvaluationContext;
import com.tradingassistant.strategy.exception.StrategyValidationException;

import java.util.Objects;

/**
 * Rules that read the market assessment or the current candle without touching
 * indicators. Each rule describes one condition; none of them scores the market.
 */
public final class AssessmentRules {

    private AssessmentRules() {
    }

    /**
     * Condition: the market structure reports the named signal.
     */
    public record StructureSignalPresent(String id,
                                         StructureSignal signal) implements StrategyRule {

        public StructureSignalPresent {
            id = IndicatorRules.Rules.requireId(id);
            signal = Objects.requireNonNull(signal, "signal must not be null");
        }

        @Override
        public RuleEvaluation evaluate(StrategyEvaluationContext context) {
            Objects.requireNonNull(context, "context must not be null");
            var structure = context.marketAssessment().structure();
            var metadata = ConditionSupport.metadata("signal", signal.name(),
                    "bias", structure.bias().name());
            if (structure.status() == AssessmentStatus.INSUFFICIENT_DATA) {
                return RuleEvaluation.insufficient(id,
                        "market structure is unavailable for this candle");
            }
            if (structure.signals().contains(signal)) {
                return RuleEvaluation.passed(id,
                        "market structure reports " + signal, metadata);
            }
            return RuleEvaluation.failed(id,
                    "market structure does not report " + signal, metadata);
        }
    }

    /**
     * Condition: the market volatility state matches the configured state.
     */
    public record MarketVolatilityState(String id,
                                        VolatilityState expected) implements StrategyRule {

        public MarketVolatilityState {
            id = IndicatorRules.Rules.requireId(id);
            expected = Objects.requireNonNull(expected, "expected must not be null");
        }

        @Override
        public RuleEvaluation evaluate(StrategyEvaluationContext context) {
            Objects.requireNonNull(context, "context must not be null");
            var volatility = context.marketAssessment().volatility();
            var metadata = ConditionSupport.metadata("state", volatility.state().name(),
                    "expected", expected.name());
            if (volatility.status() == AssessmentStatus.INSUFFICIENT_DATA) {
                return RuleEvaluation.insufficient(id, "market volatility is unavailable");
            }
            if (volatility.state() == expected) {
                return RuleEvaluation.passed(id,
                        "market volatility is " + expected, metadata);
            }
            return RuleEvaluation.failed(id,
                    "market volatility is " + volatility.state(), metadata);
        }
    }

    /**
     * Condition: the candle volume is at least the configured minimum.
     */
    public record VolumeAtLeast(String id, long minimumVolume) implements StrategyRule {

        public VolumeAtLeast {
            id = IndicatorRules.Rules.requireId(id);
            if (minimumVolume < 0) {
                throw new StrategyValidationException("minimumVolume must not be negative");
            }
        }

        @Override
        public RuleEvaluation evaluate(StrategyEvaluationContext context) {
            Objects.requireNonNull(context, "context must not be null");
            var volume = ConditionSupport.volume(context);
            if (volume.isEmpty()) {
                return RuleEvaluation.insufficient(id, "candle volume is unavailable");
            }
            var metadata = ConditionSupport.metadata("volume", Long.toString(volume.get()),
                    "minimumVolume", Long.toString(minimumVolume));
            if (volume.get() >= minimumVolume) {
                return RuleEvaluation.passed(id,
                        "candle volume is at least the configured minimum", metadata);
            }
            return RuleEvaluation.failed(id,
                    "candle volume is below the configured minimum", metadata);
        }
    }
}
