package com.tradingassistant.indicator.domain;

/**
 * Stable keys for named indicator outputs. A single-value calculation uses
 * {@link #VALUE}; multi-line calculations use their dedicated keys.
 */
public final class IndicatorValues {

    public static final String VALUE = "value";
    public static final String UPPER = "upper";
    public static final String MIDDLE = "middle";
    public static final String LOWER = "lower";
    public static final String MACD = "macd";
    public static final String SIGNAL = "signal";
    public static final String HISTOGRAM = "histogram";
    public static final String PERCENT_K = "percentK";
    public static final String PERCENT_D = "percentD";
    public static final String PLUS_DI = "plusDI";
    public static final String MINUS_DI = "minusDI";
    public static final String ADX = "adx";
    public static final String SUPERTREND = "supertrend";
    public static final String DIRECTION = "direction";

    private IndicatorValues() {
    }
}
