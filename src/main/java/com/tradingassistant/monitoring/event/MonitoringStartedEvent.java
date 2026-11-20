package com.tradingassistant.monitoring.event;

import com.tradingassistant.monitoring.domain.TradeMonitoringResult;

import java.time.Instant;
import java.util.Objects;

/** Published when the first monitored result exists for an active trade. */
public record MonitoringStartedEvent(
        TradeMonitoringResult result,
        Instant occurredAt) implements MonitoringEvent {

    public MonitoringStartedEvent {
        result = Objects.requireNonNull(result, "result must not be null");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}