package com.tradingassistant.recommendation.event;

import com.tradingassistant.recommendation.domain.RecommendationKey;

import java.time.Instant;
import java.util.Objects;

/**
 * Published when a recommendation is no longer current, for example when the
 * market session closes.
 */
public record RecommendationExpiredEvent(
        RecommendationKey key,
        String reason,
        Instant occurredAt) implements RecommendationEvent {

    public RecommendationExpiredEvent {
        key = Objects.requireNonNull(key, "key must not be null");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        occurredAt = Objects.requireNonNull(occurredAt,
                "occurredAt must not be null");
        reason = reason.trim();
    }
}
