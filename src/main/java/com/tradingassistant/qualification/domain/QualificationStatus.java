package com.tradingassistant.qualification.domain;

/**
 * Aggregate outcome of qualifying one strategy evaluation.
 *
 * <p>The status describes eligibility for a later decision stage only. It is not
 * a recommendation, a ranking, a score or a trade instruction.</p>
 */
public enum QualificationStatus {

    /** Every qualification rule passed: the evaluation is eligible downstream. */
    QUALIFIED,

    /** At least one rule failed: the evaluation is not eligible. */
    DISQUALIFIED,

    /** Some rules passed while others could not be decided. */
    PARTIAL,

    /** No rule could be decided because the required data was unavailable. */
    INSUFFICIENT_DATA,

    /** Rules produced no decision, for example after a contained rule failure. */
    UNKNOWN
}
