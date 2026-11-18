package com.tradingassistant.intelligence.domain;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Description of market structure over the configured candle window.
 *
 * @param bias    direction implied by the detected signals
 * @param signals observed structure facts, empty when none were detected
 * @param status  availability of enough candles to describe structure
 * @param summary short deterministic explanation
 */
public record StructureAssessment(
        StructureBias bias,
        Set<StructureSignal> signals,
        AssessmentStatus status,
        String summary) {

    public StructureAssessment {
        bias = Objects.requireNonNull(bias, "bias must not be null");
        signals = signals == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(signals));
        status = Objects.requireNonNull(status, "status must not be null");
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        summary = summary.trim();
    }
}
