package com.tradingassistant.intelligence.domain;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Description of the current trend aspect.
 *
 * @param state      directional description
 * @param status     availability of the inputs behind it
 * @param indicators indicator types that contributed, empty when none did
 * @param summary    short deterministic explanation
 */
public record TrendAssessment(
        TrendState state,
        AssessmentStatus status,
        Set<String> indicators,
        String summary) {

    public TrendAssessment {
        state = Objects.requireNonNull(state, "state must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        indicators = indicators == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(indicators));
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        summary = summary.trim();
    }
}
