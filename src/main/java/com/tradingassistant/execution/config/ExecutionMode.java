package com.tradingassistant.execution.config;

/**
 * Whether orders go to a paper adapter or a live broker adapter.
 *
 * <p>Nothing in this layer decides that a trade should exist; the mode only
 * selects which adapter receives an order the caller already asked for.</p>
 */
public enum ExecutionMode {
    PAPER,
    LIVE
}
