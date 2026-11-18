package com.tradingassistant.indicator.config;

import com.tradingassistant.indicator.exception.IndicatorValidationException;

import java.math.BigDecimal;

/**
 * Immutable indicator parameters. Not every indicator consumes every field;
 * each calculator documents the fields it uses. The defaults are deliberately
 * conservative values used by the common oscillator/trend indicators.
 */
public record IndicatorConfig(
        int period,
        int fastPeriod,
        int slowPeriod,
        int signalPeriod,
        int smoothing,
        BigDecimal multiplier,
        BigDecimal deviation,
        PriceSource source
) {

    public static final int MAX_PERIOD = 100_000;

    public IndicatorConfig {
        requirePeriod(period, "period");
        requirePeriod(fastPeriod, "fastPeriod");
        requirePeriod(slowPeriod, "slowPeriod");
        requirePeriod(signalPeriod, "signalPeriod");
        requirePeriod(smoothing, "smoothing");
        multiplier = positive(multiplier, "multiplier");
        deviation = positive(deviation, "deviation");
        if (source == null) {
            throw new IndicatorValidationException("source must not be null");
        }
    }

    public static IndicatorConfig defaults() {
        return new IndicatorConfig(14, 12, 26, 9, 3,
                new BigDecimal("2"), new BigDecimal("2"), PriceSource.CLOSE);
    }

    public static IndicatorConfig period(int period, PriceSource source) {
        return new IndicatorConfig(period, 12, 26, 9, 3,
                new BigDecimal("2"), new BigDecimal("2"), source);
    }

    public static IndicatorConfig macd(int fastPeriod, int slowPeriod, int signalPeriod, PriceSource source) {
        if (fastPeriod >= slowPeriod) {
            throw new IndicatorValidationException("fastPeriod must be less than slowPeriod");
        }
        return new IndicatorConfig(14, fastPeriod, slowPeriod, signalPeriod, 3,
                new BigDecimal("2"), new BigDecimal("2"), source);
    }

    public static IndicatorConfig stochasticRsi(int period, int smoothing, PriceSource source) {
        return new IndicatorConfig(period, 12, 26, 9, smoothing,
                new BigDecimal("2"), new BigDecimal("2"), source);
    }

    public static IndicatorConfig bollinger(int period, BigDecimal deviation, PriceSource source) {
        return new IndicatorConfig(period, 12, 26, 9, 3,
                new BigDecimal("2"), deviation, source);
    }

    public static IndicatorConfig keltner(int period, int atrPeriod, BigDecimal multiplier, PriceSource source) {
        return new IndicatorConfig(period, 12, 26, 9, atrPeriod,
                multiplier, new BigDecimal("2"), source);
    }

    public static IndicatorConfig supertrend(int period, BigDecimal multiplier, PriceSource source) {
        return new IndicatorConfig(period, 12, 26, 9, 3,
                multiplier, new BigDecimal("2"), source);
    }

    private static void requirePeriod(int value, String field) {
        if (value < 1 || value > MAX_PERIOD) {
            throw new IndicatorValidationException(field + " must be between 1 and " + MAX_PERIOD);
        }
    }

    private static BigDecimal positive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new IndicatorValidationException(field + " must be positive");
        }
        return value;
    }
}
