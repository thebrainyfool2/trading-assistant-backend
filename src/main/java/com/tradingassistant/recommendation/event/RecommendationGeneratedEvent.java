package com.tradingassistant.recommendation.event;

import com.tradingassistant.recommendation.domain.Recommendation;

import java.time.Instant;
import java.util.Objects;

/**
 * Published the first time a recommendation exists for a key.
 */
public record RecommendationGeneratedEvent(
        Recommendation recommendation,
        Instant occurredAt) implements RecommendationEvent {

    public RecommendationGeneratedEvent {
        recommendation = Objects.requireNonNull(recommendation,
                "recommendation must not be null");
        occurredAt = Objects.requireNonNull(occurredAt,
                "occurredAt must not be null");
    }
}
