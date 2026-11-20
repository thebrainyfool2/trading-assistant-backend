package com.tradingassistant.monitoring.persistence;

import com.tradingassistant.monitoring.domain.MonitoringAlert;
import com.tradingassistant.monitoring.domain.MonitoringAlertType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/** Persisted monitoring alert exposed by the read-only alerts endpoint. */
@Document(collection = "monitoring_alerts")
public record MonitoringAlertDocument(
        @Id String id,
        @Indexed String tradeId,
        @Indexed Instant timestamp,
        MonitoringAlertType type,
        String message,
        Map<String, String> diagnostics) {

    public static MonitoringAlertDocument from(MonitoringAlert alert) {
        return new MonitoringAlertDocument(null, alert.tradeId(), alert.timestamp(),
                alert.type(), alert.message(), alert.diagnostics());
    }

    public MonitoringAlert toDomain() {
        return new MonitoringAlert(tradeId, timestamp, type, message, diagnostics);
    }
}