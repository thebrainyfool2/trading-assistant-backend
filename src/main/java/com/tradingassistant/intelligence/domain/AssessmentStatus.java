package com.tradingassistant.intelligence.domain;

/**
 * Deterministic availability state of one aspect of an assessment, and of the
 * whole assessment.
 *
 * <p>The state exists so warm-up is never silent: a consumer can distinguish a
 * complete description from one built on the indicators that were available at
 * the time.</p>
 */
public enum AssessmentStatus {

    /** Every input this aspect needs was present and ready. */
    READY,

    /** At least one input was usable and at least one was missing or warming up. */
    PARTIAL,

    /** No input was usable, so the aspect could not be described. */
    INSUFFICIENT_DATA,

    /** Inputs were usable but the aspect could not reach a conclusion. */
    UNKNOWN
}
