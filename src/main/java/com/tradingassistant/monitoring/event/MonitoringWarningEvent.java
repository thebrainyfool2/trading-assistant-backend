package com.tradingassistant.monitoring.event;

import com.tradingassistant.monitoring.domain.MonitoringAlertType;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Published for every persisted monitoring warning or alert. */
public record MonitoringWarningEvent(
        String tradeId,
        MonitoringAlertType alertType,
        String message,
        Map<String, String> diagnostics,
        Instant occurredAt) implements MonitoringEvent {

    public MonitoringWarningEvent {
        if (tradeId == null || tradeId.isBlank()) {
            throw new IllegalArgumentException("tradeId must not be blank");
        }
        tradeId = tradeId.trim();
        alertType = Objects.requireNonNull(alertType, "alertType must not be null");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        message = message.trim();
        diagnostics = diagnostics == null ? Map.of() : Map.copyOf(diagnostics);
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}