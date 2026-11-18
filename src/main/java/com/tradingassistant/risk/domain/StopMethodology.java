package com.tradingassistant.risk.domain;

/**
 * How the protective stop distance was derived.
 *
 * <p>The enum names a calculation, not an instruction: the model states that a
 * distance was derived from average true range, and nothing more.</p>
 */
public enum StopMethodology {

    /** Distance derived from the ATR indicator and a volatility-adjusted multiple. */
    ATR,

    /** No distance could be derived, so no stop boundary exists. */
    NONE
}
