package com.tradingassistant.strategy.rule;

import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.options.domain.LiquidityState;
import com.tradingassistant.options.domain.VolatilityState;
import com.tradingassistant.strategy.api.StrategyRule;
import com.tradingassistant.strategy.condition.ConditionSupport;
import com.tradingassistant.strategy.domain.RuleEvaluation;
import com.tradingassistant.strategy.domain.StrategyEvaluationContext;
import com.tradingassistant.strategy.exception.StrategyValidationException;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Rules that describe the derivatives market through the options assessment.
 *
 * <p>Each rule requires an options assessment and reports
 * {@code INSUFFICIENT_DATA} when it is absent or when the specific section it
 * needs is unavailable. No rule produces a trade direction.</p>
 */
public final class OptionsRules {

    private OptionsRules() {
    }

    /**
     * Condition: option implied volatility is expanding.
     */
    public record IvExpansion(String id) implements StrategyRule {

        public IvExpansion {
            id = IndicatorRules.Rules.requireId(id);
        }

        @Override
        public RuleEvaluation evaluate(StrategyEvaluationContext context) {
            Objects.requireNonNull(context, "context must not be null");
            if (context.optionsAssessment().isEmpty()) {
                return RuleEvaluation.insufficient(id, "options assessment is unavailable");
            }
            var volatility = context.optionsAssessment().get().volatility();
            var metadata = ConditionSupport.metadata("state", volatility.state().name(),
                    "atmImpliedVolatility", ConditionSupport.format(
                            volatility.atmImpliedVolatility().orElse(null)));
            if (volatility.status() == AssessmentStatus.INSUFFICIENT_DATA
                    || volatility.state() == VolatilityState.UNKNOWN) {
                return RuleEvaluation.insufficient(id,
                        "implied volatility state is unavailable");
            }
            if (volatility.state() == VolatilityState.EXPANDING) {
                return RuleEvaluation.passed(id, "implied volatility is expanding", metadata);
            }
            return RuleEvaluation.failed(id,
                    "implied volatility is " + volatility.state(), metadata);
        }
    }

    /**
     * Condition: the chain put/call ratio by open interest is at least the
     * configured threshold.
     */
    public record PcrAtLeast(String id, BigDecimal threshold) implements StrategyRule {

        public PcrAtLeast {
            id = IndicatorRules.Rules.requireId(id);
            threshold = IndicatorRules.Rules.requireThreshold(threshold, "threshold");
            if (threshold.signum() <= 0) {
                throw new StrategyValidationException("threshold must be positive");
            }
        }

        @Override
        public RuleEvaluation evaluate(StrategyEvaluationContext context) {
            Objects.requireNonNull(context, "context must not be null");
            if (context.optionsAssessment().isEmpty()) {
                return RuleEvaluation.insufficient(id, "options assessment is unavailable");
            }
            var ratio = context.optionsAssessment().get().putCallRatio()
                    .chainOpenInterestRatio();
            if (ratio.isEmpty()) {
                return RuleEvaluation.insufficient(id,
                        "chain put/call ratio by open interest is unavailable");
            }
            var metadata = ConditionSupport.metadata("putCallRatio",
                    ConditionSupport.format(ratio.get()),
                    "threshold", ConditionSupport.format(threshold));
            if (ratio.get().compareTo(threshold) >= 0) {
                return RuleEvaluation.passed(id,
                        "put/call ratio is at least the configured threshold", metadata);
            }
            return RuleEvaluation.failed(id,
                    "put/call ratio is below the configured threshold", metadata);
        }
    }

    /**
     * Condition: the max-pain strike is within the configured distance of spot.
     */
    public record MaxPainWithinPercent(String id,
                                       BigDecimal tolerancePercent) implements StrategyRule {

        public MaxPainWithinPercent {
            id = IndicatorRules.Rules.requireId(id);
            tolerancePercent = IndicatorRules.Rules.requireThreshold(tolerancePercent,
                    "tolerancePercent");
            if (tolerancePercent.signum() < 0) {
                throw new StrategyValidationException(
                        "tolerancePercent must not be negative");
            }
        }

        @Override
        public RuleEvaluation evaluate(StrategyEvaluationContext context) {
            Objects.requireNonNull(context, "context must not be null");
            if (context.optionsAssessment().isEmpty()) {
                return RuleEvaluation.insufficient(id, "options assessment is unavailable");
            }
            var options = context.optionsAssessment().get();
            var maxPain = options.maxPain().maxPainStrike();
            BigDecimal spot = options.chain().spotPrice();
            if (maxPain.isEmpty() || spot.signum() <= 0) {
                return RuleEvaluation.insufficient(id, "max pain is unavailable");
            }
            BigDecimal distancePercent = maxPain.get().subtract(spot).abs()
                    .divide(spot, com.tradingassistant.options.math.DecimalMath.CONTEXT)
                    .multiply(new BigDecimal("100"));
            var metadata = ConditionSupport.metadata("maxPain",
                    ConditionSupport.format(maxPain.get()),
                    "spot", ConditionSupport.format(spot),
                    "distancePercent", ConditionSupport.format(distancePercent),
                    "tolerancePercent", ConditionSupport.format(tolerancePercent));
            if (distancePercent.compareTo(tolerancePercent) <= 0) {
                return RuleEvaluation.passed(id,
                        "max pain is inside the configured distance from spot", metadata);
            }
            return RuleEvaluation.failed(id,
                    "max pain is outside the configured distance from spot", metadata);
        }
    }

    /**
     * Condition: the expected expiry move is at least the configured percentage
     * of spot.
     */
    public record ExpectedMoveAtLeastPercent(String id,
                                             BigDecimal percentOfSpot) implements StrategyRule {

        public ExpectedMoveAtLeastPercent {
            id = IndicatorRules.Rules.requireId(id);
            percentOfSpot = IndicatorRules.Rules.requireThreshold(percentOfSpot,
                    "percentOfSpot");
            if (percentOfSpot.signum() < 0) {
                throw new StrategyValidationException("percentOfSpot must not be negative");
            }
        }

        @Override
        public RuleEvaluation evaluate(StrategyEvaluationContext context) {
            Objects.requireNonNull(context, "context must not be null");
            if (context.optionsAssessment().isEmpty()) {
                return RuleEvaluation.insufficient(id, "options assessment is unavailable");
            }
            var options = context.optionsAssessment().get();
            var move = options.expectedMove().expiryMove();
            BigDecimal spot = options.chain().spotPrice();
            if (move.isEmpty() || spot.signum() <= 0) {
                return RuleEvaluation.insufficient(id, "expected move is unavailable");
            }
            BigDecimal percent = move.get()
                    .divide(spot, com.tradingassistant.options.math.DecimalMath.CONTEXT)
                    .multiply(new BigDecimal("100"));
            var metadata = ConditionSupport.metadata("expiryMove",
                    ConditionSupport.format(move.get()),
                    "percentOfSpot", ConditionSupport.format(percent),
                    "threshold", ConditionSupport.format(percentOfSpot));
            if (percent.compareTo(percentOfSpot) >= 0) {
                return RuleEvaluation.passed(id,
                        "expected move is at least the configured share of spot", metadata);
            }
            return RuleEvaluation.failed(id,
                    "expected move is below the configured share of spot", metadata);
        }
    }

    /**
     * Condition: the chain is described as liquid.
     */
    public record LiquidityAtLeast(String id) implements StrategyRule {

        public LiquidityAtLeast {
            id = IndicatorRules.Rules.requireId(id);
        }

        @Override
        public RuleEvaluation evaluate(StrategyEvaluationContext context) {
            Objects.requireNonNull(context, "context must not be null");
            if (context.optionsAssessment().isEmpty()) {
                return RuleEvaluation.insufficient(id, "options assessment is unavailable");
            }
            var liquidity = context.optionsAssessment().get().liquidity();
            var metadata = ConditionSupport.metadata("liquidity", liquidity.state().name(),
                    "tradability", liquidity.tradability().name());
            if (liquidity.status() == AssessmentStatus.INSUFFICIENT_DATA
                    || liquidity.state() == LiquidityState.UNKNOWN) {
                return RuleEvaluation.insufficient(id, "liquidity is unavailable");
            }
            if (liquidity.state() == LiquidityState.LIQUID) {
                return RuleEvaluation.passed(id, "chain liquidity is liquid", metadata);
            }
            return RuleEvaluation.failed(id,
                    "chain liquidity is " + liquidity.state(), metadata);
        }
    }
}
