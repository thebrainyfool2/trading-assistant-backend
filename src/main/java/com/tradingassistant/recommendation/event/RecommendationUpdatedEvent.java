package com.tradingassistant.recommendation.event;

import com.tradingassistant.recommendation.domain.Recommendation;

import java.time.Instant;
import java.util.Objects;

/**
 * Published when a key's recommendation changes. Identical re-evaluations do
 * not publish an update.
 */
public record RecommendationUpdatedEvent(
        Recommendation recommendation,
        Instant occurredAt) implements RecommendationEvent {

    public RecommendationUpdatedEvent {
        recommendation = Objects.requireNonNull(recommendation,
                "recommendation must not be null");
        occurredAt = Objects.requireNonNull(occurredAt,
                "occurredAt must not be null");
    }
}
