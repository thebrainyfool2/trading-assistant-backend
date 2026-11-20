package com.tradingassistant.recommendation.domain;

import java.util.Objects;

/**
 * Ranked view of a recommendation. Rank is one-based and deterministic for a
 * fixed batch.
 */
public record RankedRecommendation(int rank, Recommendation recommendation) {

    public RankedRecommendation {
        if (rank <= 0) {
            throw new IllegalArgumentException("rank must be positive");
        }
        recommendation = Objects.requireNonNull(recommendation,
                "recommendation must not be null");
    }
}
