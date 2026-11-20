package com.tradingassistant.monitoring.event;

import com.tradingassistant.monitoring.domain.ExitRecommendation;
import com.tradingassistant.monitoring.domain.TradeMonitoringResult;

import java.time.Instant;
import java.util.Objects;

/** Published when the deterministic exit recommendation changes. */
public record ExitRecommendedEvent(
        TradeMonitoringResult result,
        ExitRecommendation previousRecommendation,
        ExitRecommendation currentRecommendation,
        Instant occurredAt) implements MonitoringEvent {

    public ExitRecommendedEvent {
        result = Objects.requireNonNull(result, "result must not be null");
        previousRecommendation = Objects.requireNonNull(previousRecommendation,
                "previousRecommendation must not be null");
        currentRecommendation = Objects.requireNonNull(currentRecommendation,
                "currentRecommendation must not be null");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}