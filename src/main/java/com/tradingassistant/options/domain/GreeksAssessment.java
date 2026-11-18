package com.tradingassistant.options.domain;

import com.tradingassistant.intelligence.domain.AssessmentStatus;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of one Greek evaluation.
 *
 * @param greeks  computed sensitivities, empty when they could not be produced
 * @param status  availability of the evaluation
 * @param model   identification of the pricing model used
 * @param summary short deterministic explanation
 */
public record GreeksAssessment(
        Optional<Greeks> greeks,
        AssessmentStatus status,
        String model,
        String summary) {

    public GreeksAssessment {
        greeks = greeks == null ? Optional.empty() : greeks;
        status = Objects.requireNonNull(status, "status must not be null");
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        model = model.trim();
        summary = summary.trim();
        if (greeks.isPresent() != (status == AssessmentStatus.READY)) {
            throw new IllegalArgumentException("greeks presence must match a READY status");
        }
    }

    public boolean ready() {
        return status == AssessmentStatus.READY;
    }
}
