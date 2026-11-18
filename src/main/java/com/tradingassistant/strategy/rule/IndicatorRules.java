package com.tradingassistant.strategy.rule;

import com.tradingassistant.indicator.domain.IndicatorValues;
import com.tradingassistant.strategy.api.StrategyRule;
import com.tradingassistant.strategy.condition.ConditionSupport;
import com.tradingassistant.strategy.domain.RuleEvaluation;
import com.tradingassistant.strategy.domain.StrategyEvaluationContext;
import com.tradingassistant.strategy.exception.StrategyValidationException;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Rules that compare the current candle close or an indicator value with a
 * configured threshold. Each rule is an immutable record and can be reused by
 * several strategy definitions without sharing state.
 */
public final class IndicatorRules {

    private IndicatorRules() {
    }

    /**
     * Condition: the close is above the value of the named indicator.
     */
    public record CloseAboveIndicator(String id, String indicator) implements StrategyRule {

        public CloseAboveIndicator {
            id = Rules.requireId(id);
            indicator = Rules.requireName(indicator, "indicator");
        }

        @Override
        public RuleEvaluation evaluate(StrategyEvaluationContext context) {
            Objects.requireNonNull(context, "context must not be null");
            var close = ConditionSupport.close(context);
            var value = ConditionSupport.value(context, indicator);
            if (close.isEmpty() || value.isEmpty()) {
                return RuleEvaluation.insufficient(id,
                        "close or " + indicator + " value is unavailable");
            }
            var metadata = ConditionSupport.metadata("indicator", indicator,
                    "close", ConditionSupport.format(close.get()),
                    "value", ConditionSupport.format(value.get()));
            if (close.get().compareTo(value.get()) > 0) {
                return RuleEvaluation.passed(id,
                        "close is above " + indicator, metadata);
            }
            return RuleEvaluation.failed(id,
                    "close is not above " + indicator, metadata);
        }
    }

    /**
     * Condition: an indicator value is at least the configured threshold.
     */
    public record IndicatorAtLeast(String id, String indicator, String valueKey,
                                   BigDecimal threshold) implements StrategyRule {

        public IndicatorAtLeast {
            id = Rules.requireId(id);
            indicator = Rules.requireName(indicator, "indicator");
            valueKey = Rules.requireName(valueKey, "valueKey");
            threshold = Rules.requireThreshold(threshold, "threshold");
        }

        @Override
        public RuleEvaluation evaluate(StrategyEvaluationContext context) {
            Objects.requireNonNull(context, "context must not be null");
            var value = ConditionSupport.value(context, indicator, valueKey);
            if (value.isEmpty()) {
                return RuleEvaluation.insufficient(id,
                        indicator + " " + valueKey + " is unavailable");
            }
            var metadata = ConditionSupport.metadata("indicator", indicator,
                    "value", ConditionSupport.format(value.get()),
                    "threshold", ConditionSupport.format(threshold));
            if (value.get().compareTo(threshold) >= 0) {
                return RuleEvaluation.passed(id,
                        indicator + " is at least the configured threshold", metadata);
            }
            return RuleEvaluation.failed(id,
                    indicator + " is below the configured threshold", metadata);
        }
    }

    /**
     * Condition: an indicator value is at most the configured threshold.
     */
    public record IndicatorAtMost(String id, String indicator, String valueKey,
                                  BigDecimal threshold) implements StrategyRule {

        public IndicatorAtMost {
            id = Rules.requireId(id);
            indicator = Rules.requireName(indicator, "indicator");
            valueKey = Rules.requireName(valueKey, "valueKey");
            threshold = Rules.requireThreshold(threshold, "threshold");
        }

        @Override
        public RuleEvaluation evaluate(StrategyEvaluationContext context) {
            Objects.requireNonNull(context, "context must not be null");
            var value = ConditionSupport.value(context, indicator, valueKey);
            if (value.isEmpty()) {
                return RuleEvaluation.insufficient(id,
                        indicator + " " + valueKey + " is unavailable");
            }
            var metadata = ConditionSupport.metadata("indicator", indicator,
                    "value", ConditionSupport.format(value.get()),
                    "threshold", ConditionSupport.format(threshold));
            if (value.get().compareTo(threshold) <= 0) {
                return RuleEvaluation.passed(id,
                        indicator + " is at most the configured threshold", metadata);
            }
            return RuleEvaluation.failed(id,
                    indicator + " is above the configured threshold", metadata);
        }
    }

    /**
     * Condition: the Supertrend direction is positive.
     */
    public record SupertrendBullish(String id) implements StrategyRule {

        public SupertrendBullish {
            id = Rules.requireId(id);
        }

        @Override
        public RuleEvaluation evaluate(StrategyEvaluationContext context) {
            Objects.requireNonNull(context, "context must not be null");
            var direction = ConditionSupport.value(context, "SUPERTREND",
                    IndicatorValues.DIRECTION);
            if (direction.isEmpty()) {
                return RuleEvaluation.insufficient(id, "SUPERTREND direction is unavailable");
            }
            var metadata = ConditionSupport.metadata("direction",
                    ConditionSupport.format(direction.get()));
            if (direction.get().signum() > 0) {
                return RuleEvaluation.passed(id, "Supertrend direction is positive", metadata);
            }
            return RuleEvaluation.failed(id, "Supertrend direction is not positive", metadata);
        }
    }

    /**
     * Condition: the MACD histogram is positive.
     */
    public record MacdHistogramPositive(String id) implements StrategyRule {

        public MacdHistogramPositive {
            id = Rules.requireId(id);
        }

        @Override
        public RuleEvaluation evaluate(StrategyEvaluationContext context) {
            Objects.requireNonNull(context, "context must not be null");
            var histogram = ConditionSupport.value(context, "MACD",
                    IndicatorValues.HISTOGRAM);
            if (histogram.isEmpty()) {
                return RuleEvaluation.insufficient(id, "MACD histogram is unavailable");
            }
            var metadata = ConditionSupport.metadata("histogram",
                    ConditionSupport.format(histogram.get()));
            if (histogram.get().signum() > 0) {
                return RuleEvaluation.passed(id, "MACD histogram is positive", metadata);
            }
            return RuleEvaluation.failed(id, "MACD histogram is not positive", metadata);
        }
    }

    /**
     * Shared validation used by every rule record in this package.
     */
    static final class Rules {

        private Rules() {
        }

        static String requireId(String value) {
            if (value == null || value.isBlank()) {
                throw new StrategyValidationException("rule id must not be blank");
            }
            return value.trim();
        }

        static String requireName(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new StrategyValidationException(field + " must not be blank");
            }
            return value.trim();
        }

        static BigDecimal requireThreshold(BigDecimal value, String field) {
            if (value == null) {
                throw new StrategyValidationException(field + " must not be null");
            }
            return value;
        }
    }
}
