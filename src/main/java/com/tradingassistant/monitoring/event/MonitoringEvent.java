package com.tradingassistant.monitoring.event;

import java.time.Instant;

/** Separate monitoring event hierarchy; the market event contract is untouched. */
public sealed interface MonitoringEvent
        permits MonitoringStartedEvent, MonitoringUpdatedEvent,
        MonitoringStateChangedEvent, MonitoringWarningEvent,
        ExitRecommendedEvent, MonitoringStoppedEvent {

    Instant occurredAt();
}