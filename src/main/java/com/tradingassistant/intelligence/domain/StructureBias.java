package com.tradingassistant.intelligence.domain;

/**
 * Direction of the swing structure formed by recent candles. Structure is a
 * description of highs and lows, not a trade view.
 */
public enum StructureBias {
    UPTREND,
    DOWNTREND,
    RANGING,
    UNKNOWN
}
