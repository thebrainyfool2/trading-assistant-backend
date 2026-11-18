package com.tradingassistant.risk.domain;

/**
 * Aggregate outcome of assessing the risk boundaries of one qualified trade.
 *
 * <p>The status describes whether the configured risk policies are satisfied. It
 * is not a recommendation, an approval to trade, a ranking or a score.</p>
 */
public enum RiskStatus {

    /** Every risk rule passed: the computed boundaries stay inside policy. */
    WITHIN_LIMITS,

    /** At least one risk boundary was breached. */
    LIMIT_BREACHED,

    /** Some boundaries could be checked while others could not. */
    PARTIAL,

    /** No boundary could be checked because the required data was unavailable. */
    INSUFFICIENT_DATA,

    /** Rules produced no decision, for example after a contained rule failure. */
    UNKNOWN
}
