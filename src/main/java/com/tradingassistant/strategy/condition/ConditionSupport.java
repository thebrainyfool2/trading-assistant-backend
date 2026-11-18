package com.tradingassistant.strategy.condition;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.domain.IndicatorValues;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.options.domain.ChainSummary;
import com.tradingassistant.strategy.domain.StrategyEvaluationContext;
import com.tradingassistant.strategy.domain.StrategyInput;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only helpers shared by rule implementations.
 *
 * <p>Every helper returns an empty optional when the input is absent, so a rule
 * can report {@code INSUFFICIENT_DATA} with a precise reason instead of
 * inventing a value. Nothing here decides a condition; the rules do.</p>
 */
public final class ConditionSupport {

    private ConditionSupport() {
    }

    /**
     * Latest value of an indicator under one of the standard value keys.
     */
    public static Optional<BigDecimal> value(StrategyEvaluationContext context,
                                             String indicator,
                                             String valueKey) {
        Objects.requireNonNull(context, "context must not be null");
        if (indicator == null || indicator.isBlank() || valueKey == null || valueKey.isBlank()) {
            return Optional.empty();
        }
        return context.indicator(indicator.trim().toUpperCase(Locale.ROOT))
                .map(IndicatorResult::values)
                .map(values -> values.get(valueKey));
    }

    /**
     * Latest primary value of an indicator.
     */
    public static Optional<BigDecimal> value(StrategyEvaluationContext context,
                                             String indicator) {
        return value(context, indicator, IndicatorValues.VALUE);
    }

    public static Optional<BigDecimal> close(StrategyEvaluationContext context) {
        Objects.requireNonNull(context, "context must not be null");
        return context.candle().map(Candle::close);
    }

    public static Optional<Long> volume(StrategyEvaluationContext context) {
        Objects.requireNonNull(context, "context must not be null");
        return context.candle().map(Candle::volume);
    }

    /**
     * Spot price of the assessed chain, when an option assessment exists.
     */
    public static Optional<BigDecimal> spot(StrategyEvaluationContext context) {
        Objects.requireNonNull(context, "context must not be null");
        return context.optionsAssessment()
                .map(options -> options.chain())
                .map(ChainSummary::spotPrice);
    }

    /**
     * True when the context can supply the declared input.
     */
    public static boolean available(StrategyEvaluationContext context, StrategyInput input) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(input, "input must not be null");
        return switch (input) {
            case MARKET_ASSESSMENT -> context.marketAssessment() != null;
            case OPTIONS_ASSESSMENT -> context.optionsAssessment().isPresent();
            case CANDLE -> context.candle().isPresent();
            case INDICATORS -> !context.indicators().isEmpty();
        };
    }

    public static Map<String, String> metadata(String... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("metadata requires key/value pairs");
        }
        java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>();
        for (int index = 0; index < keysAndValues.length; index += 2) {
            values.put(keysAndValues[index], keysAndValues[index + 1]);
        }
        return Map.copyOf(values);
    }

    public static String format(BigDecimal value) {
        if (value == null) {
            return "unavailable";
        }
        BigDecimal scaled = value.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros();
        return scaled.scale() < 0 ? scaled.setScale(0).toPlainString() : scaled.toPlainString();
    }
}
