package com.tradingassistant.risk.domain;

/**
 * Outcome of a single risk rule.
 *
 * <p>Separate from {@link RiskStatus} on purpose: a rule checks one boundary,
 * while the aggregate status describes the whole assessment. A rule can therefore
 * report missing data without pretending a limit was breached.</p>
 */
public enum RiskRuleStatus {

    /** The boundary holds. */
    PASSED,

    /** The boundary was breached. */
    FAILED,

    /** The boundary could not be checked because an input was unavailable. */
    INSUFFICIENT_DATA,

    /** The rule failed unexpectedly and the failure was contained. */
    UNKNOWN
}
