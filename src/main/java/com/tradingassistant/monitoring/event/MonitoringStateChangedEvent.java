package com.tradingassistant.monitoring.event;

import com.tradingassistant.monitoring.domain.MonitoringState;
import com.tradingassistant.monitoring.domain.TradeMonitoringResult;

import java.time.Instant;
import java.util.Objects;

/** Published when the monitoring state changes. */
public record MonitoringStateChangedEvent(
        TradeMonitoringResult result,
        MonitoringState previousState,
        MonitoringState currentState,
        Instant occurredAt) implements MonitoringEvent {

    public MonitoringStateChangedEvent {
        result = Objects.requireNonNull(result, "result must not be null");
        previousState = Objects.requireNonNull(previousState,
                "previousState must not be null");
        currentState = Objects.requireNonNull(currentState, "currentState must not be null");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}