package com.tradingassistant.intelligence.config;

/**
 * How an analyzer treats inputs that are still warming up or absent.
 *
 * <p>Neither value ever fabricates an input. {@link #PERMISSIVE} uses the inputs
 * that are usable and marks the aspect {@code PARTIAL};
 * {@link #STRICT} refuses to describe the aspect until every expected input is
 * ready, and marks it {@code UNKNOWN}. Both are deterministic and both report
 * the missing inputs through the aspect status.</p>
 */
public enum WarmupPolicy {

    /**
     * Describe with whatever inputs are ready; status records the gap.
     */
    PERMISSIVE,

    /**
     * Report {@code UNKNOWN} for any aspect whose inputs are not all ready.
     */
    STRICT;

    /**
     * True when this policy refuses to describe an incompletely ready aspect.
     */
    public boolean blocksAssessment(com.tradingassistant.intelligence.domain.AssessmentStatus status) {
        return this == STRICT && status != com.tradingassistant.intelligence.domain.AssessmentStatus.READY;
    }
}
