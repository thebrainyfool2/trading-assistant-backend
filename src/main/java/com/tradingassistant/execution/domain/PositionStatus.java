package com.tradingassistant.execution.domain;

/**
 * Lifecycle state of a position.
 */
public enum PositionStatus {
    OPENING,
    OPEN,
    PARTIALLY_CLOSED,
    CLOSED
}
