package com.tradingassistant.monitoring.domain;

/**
 * Lifecycle of a manually entered trade. Monitoring never changes this state
 * automatically; the owner closes the trade after exiting it outside the
 * application.
 */
public enum ManualTradeStatus {
    ACTIVE,
    CLOSED
}