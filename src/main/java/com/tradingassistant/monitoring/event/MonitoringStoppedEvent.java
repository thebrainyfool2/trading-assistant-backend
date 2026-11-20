package com.tradingassistant.monitoring.event;

import java.time.Instant;
import java.util.Objects;

/** Published when the owner closes a manually recorded trade. */
public record MonitoringStoppedEvent(
        String tradeId,
        String reason,
        Instant occurredAt) implements MonitoringEvent {

    public MonitoringStoppedEvent {
        if (tradeId == null || tradeId.isBlank()) {
            throw new IllegalArgumentException("tradeId must not be blank");
        }
        tradeId = tradeId.trim();
        reason = reason == null || reason.isBlank() ? "trade closed" : reason.trim();
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}