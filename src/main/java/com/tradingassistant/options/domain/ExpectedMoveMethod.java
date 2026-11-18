package com.tradingassistant.options.domain;

/**
 * Calculation used for the expected move.
 */
public enum ExpectedMoveMethod {
    /** Spot multiplied by the ATM implied volatility and the square root of time. */
    VOLATILITY,

    /** The price of the ATM call plus the ATM put. */
    STRADDLE
}
