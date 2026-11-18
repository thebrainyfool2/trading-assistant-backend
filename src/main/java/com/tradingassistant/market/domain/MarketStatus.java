package com.tradingassistant.market.domain;

/**
 * Normalized market session state. {@link #UNKNOWN} is used when a timestamp is
 * unavailable; it is never inferred from a missing quote field.
 */
public enum MarketStatus {
    PRE_OPEN,
    OPEN,
    CLOSED,
    AUCTION,
    UNKNOWN
}
