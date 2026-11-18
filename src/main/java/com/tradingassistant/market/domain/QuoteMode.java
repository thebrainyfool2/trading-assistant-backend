package com.tradingassistant.market.domain;

/**
 * Requested quote detail. The adapter decides how to request each mode from the
 * broker; application code only selects the domain mode.
 */
public enum QuoteMode {
    LTP,
    OHLC,
    FULL
}
