package com.tradingassistant.market.domain;

/**
 * Live-feed detail level. The names describe the normalized payload rather than
 * any provider protocol value.
 */
public enum TickMode {
    LTP,
    QUOTE,
    SNAPSHOT
}
