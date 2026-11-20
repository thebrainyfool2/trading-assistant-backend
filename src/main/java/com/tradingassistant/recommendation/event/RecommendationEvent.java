package com.tradingassistant.recommendation.event;

import java.time.Instant;

/**
 * Recommendation-specific event hierarchy. The existing sealed market event
 * contract is intentionally untouched.
 */
public sealed interface RecommendationEvent
        permits RecommendationGeneratedEvent, RecommendationUpdatedEvent,
        RecommendationExpiredEvent {

    Instant occurredAt();
}
