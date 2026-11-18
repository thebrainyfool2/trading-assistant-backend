package com.tradingassistant.execution.domain;

/**
 * Direction of an open position.
 */
public enum PositionDirection {
    LONG,
    SHORT;

    /** Sign applied to price differences when computing profit and loss. */
    public int signum() {
        return this == LONG ? 1 : -1;
    }

    /** Direction opened by a given order side. */
    public static PositionDirection of(OrderSide side) {
        return side == OrderSide.BUY ? LONG : SHORT;
    }
}
