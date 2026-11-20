package com.tradingassistant.monitoring.config;

import com.tradingassistant.monitoring.domain.MonitoringInterval;
import com.tradingassistant.monitoring.exception.MonitoringValidationException;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;

/** External, fully configurable monitoring policy. */
@ConfigurationProperties(prefix = "app.monitoring")
public record MonitoringProperties(
        boolean enabled,
        BigDecimal warningConfidence,
        BigDecimal exitConfidence,
        BigDecimal confidenceDeterioration,
        BigDecimal edgeDeterioration,
        BigDecimal componentDeterioration,
        Integer warningComponentCount,
        Integer atRiskComponentCount,
        BigDecimal strategyDisagreementScore,
        Duration maximumStaleDuration,
        String monitoringInterval) {

    public MonitoringConfig toConfig() {
        if (monitoringInterval == null || monitoringInterval.isBlank()) {
            throw new MonitoringValidationException(
                    "app.monitoring.monitoring-interval must be configured");
        }
        MonitoringInterval interval;
        try {
            interval = MonitoringInterval.valueOf(
                    monitoringInterval.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new MonitoringValidationException(
                    "unsupported app.monitoring.monitoring-interval: "
                            + monitoringInterval, failure);
        }
        return new MonitoringConfig(enabled, warningConfidence, exitConfidence,
                confidenceDeterioration, edgeDeterioration, componentDeterioration,
                required(warningComponentCount, "warningComponentCount"),
                required(atRiskComponentCount, "atRiskComponentCount"),
                strategyDisagreementScore, maximumStaleDuration, interval);
    }

    private static int required(Integer value, String field) {
        if (value == null) {
            throw new MonitoringValidationException(
                    "app.monitoring." + field + " must be configured");
        }
        return value;
    }
}