package com.tradingassistant.monitoring.domain;

import java.util.Map;
import java.util.Objects;

/** One explainable reason behind a monitoring state or exit recommendation. */
public record MonitoringWarning(
        MonitoringAlertType type,
        String message,
        Map<String, String> diagnostics) {

    public MonitoringWarning {
        type = Objects.requireNonNull(type, "type must not be null");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        message = message.trim();
        diagnostics = diagnostics == null ? Map.of() : Map.copyOf(diagnostics);
    }
}