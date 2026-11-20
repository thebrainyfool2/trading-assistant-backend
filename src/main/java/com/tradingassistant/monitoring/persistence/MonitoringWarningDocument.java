package com.tradingassistant.monitoring.persistence;

import com.tradingassistant.monitoring.domain.MonitoringAlertType;
import com.tradingassistant.monitoring.domain.MonitoringWarning;

import java.util.Map;

/** Embedded warning representation shared by results and history. */
public record MonitoringWarningDocument(
        MonitoringAlertType type,
        String message,
        Map<String, String> diagnostics) {

    public static MonitoringWarningDocument from(MonitoringWarning warning) {
        return new MonitoringWarningDocument(warning.type(), warning.message(),
                warning.diagnostics());
    }

    public MonitoringWarning toDomain() {
        return new MonitoringWarning(type, message, diagnostics);
    }
}