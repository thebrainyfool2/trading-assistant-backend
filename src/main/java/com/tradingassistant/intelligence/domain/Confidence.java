package com.tradingassistant.intelligence.domain;

import java.util.Objects;

/**
 * Confidence metadata for an assessment: a level plus the basis that produced
 * it. The basis is descriptive text for diagnostics and never a score.
 *
 * @param level level derived from how many aspects were ready
 * @param basis short description of the derivation
 */
public record Confidence(ConfidenceLevel level, String basis) {

    public Confidence {
        level = Objects.requireNonNull(level, "level must not be null");
        if (basis == null || basis.isBlank()) {
            throw new IllegalArgumentException("basis must not be blank");
        }
        basis = basis.trim();
    }

    public static Confidence unknown(String basis) {
        return new Confidence(ConfidenceLevel.UNKNOWN, basis);
    }
}
