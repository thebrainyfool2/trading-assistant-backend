package com.tradingassistant.platform.live;

import java.util.List;

/**
 * The complete indicator set required by the deterministic decision engine.
 *
 * <p>Keeping the list explicit makes the live subscription reproducible even if
 * the indicator registry later gains a new calculator. A new decision input must
 * be added here deliberately, reviewed and tested; it cannot appear silently
 * because a registry changed.</p>
 */
public final class LiveIndicators {

    /**
     * Every calculator currently relevant to market, strategy and risk decisions.
     * The order is stable for diagnostics and subscription construction.
     */
    public static final List<String> ALL = List.of(
            "SMA",
            "EMA",
            "WMA",
            "RSI",
            "MACD",
            "STOCH_RSI",
            "ATR",
            "BOLLINGER",
            "KELTNER",
            "DONCHIAN",
            "ADX",
            "DMI",
            "VWAP",
            "OBV",
            "CMF",
            "SUPERTREND");

    private LiveIndicators() {
    }

    public static String[] asArray() {
        return ALL.toArray(String[]::new);
    }
}
