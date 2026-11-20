package com.tradingassistant.monitoring.event;

import com.tradingassistant.monitoring.domain.TradeMonitoringResult;

import java.time.Instant;
import java.util.Objects;

/** Published when a monitoring evaluation changes the observable result. */
public record MonitoringUpdatedEvent(
        TradeMonitoringResult result,
        Instant occurredAt) implements MonitoringEvent {

    public MonitoringUpdatedEvent {
        result = Objects.requireNonNull(result, "result must not be null");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}