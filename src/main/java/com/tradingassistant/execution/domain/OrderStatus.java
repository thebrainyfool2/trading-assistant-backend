package com.tradingassistant.execution.domain;

/**
 * Lifecycle state of one order.
 *
 * <p>Transitions are enforced by {@code OrderStateMachine}: an order moves
 * forward only, and a terminal state can never be reopened.</p>
 */
public enum OrderStatus {

    CREATED,
    VALIDATED,
    SUBMITTED,
    PENDING,
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED,
    REJECTED,
    FAILED,
    EXPIRED;

    /** True when the order still consumes risk and blocks margin. */
    public boolean active() {
        return this == CREATED || this == VALIDATED || this == SUBMITTED
                || this == PENDING || this == PARTIALLY_FILLED;
    }

    /** True when no further transition is expected. */
    public boolean terminal() {
        return this == FILLED || this == CANCELLED || this == REJECTED
                || this == FAILED || this == EXPIRED;
    }
}
