package com.tradingassistant.qualification.domain;

/**
 * Outcome of a single qualification rule.
 *
 * <p>Separate from {@link QualificationStatus} on purpose: a rule decides a
 * condition, while the aggregate status describes the whole qualification. A rule
 * can therefore report {@code INSUFFICIENT_DATA} without pretending the
 * qualification itself was disqualified.</p>
 */
public enum RuleStatus {

    /** The condition holds. */
    PASSED,

    /** The condition does not hold. */
    FAILED,

    /** The condition could not be decided because an input was unavailable. */
    INSUFFICIENT_DATA,

    /** The rule failed unexpectedly and the failure was contained. */
    UNKNOWN
}
