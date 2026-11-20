package com.tradingassistant.monitoring.config;

import com.tradingassistant.monitoring.domain.MonitoringInterval;
import com.tradingassistant.monitoring.exception.MonitoringValidationException;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Validated monitoring policy. There are no hidden thresholds: every boundary
 * used by the engine comes from this value.
 */
public record MonitoringConfig(
        boolean enabled,
        BigDecimal warningConfidence,
        BigDecimal exitConfidence,
        BigDecimal confidenceDeterioration,
        BigDecimal edgeDeterioration,
        BigDecimal componentDeterioration,
        int warningComponentCount,
        int atRiskComponentCount,
        BigDecimal strategyDisagreementScore,
        Duration maximumStaleDuration,
        MonitoringInterval interval) {

    public MonitoringConfig {
        warningConfidence = percentage(warningConfidence, "warningConfidence");
        exitConfidence = percentage(exitConfidence, "exitConfidence");
        if (exitConfidence.compareTo(warningConfidence) > 0) {
            throw new MonitoringValidationException(
                    "exitConfidence must not exceed warningConfidence");
        }
        confidenceDeterioration = nonNegative(confidenceDeterioration,
                "confidenceDeterioration");
        edgeDeterioration = nonNegative(edgeDeterioration, "edgeDeterioration");
        componentDeterioration = nonNegative(componentDeterioration,
                "componentDeterioration");
        if (warningComponentCount <= 0) {
            throw new MonitoringValidationException(
                    "warningComponentCount must be positive");
        }
        if (atRiskComponentCount < warningComponentCount) {
            throw new MonitoringValidationException(
                    "atRiskComponentCount must not be below warningComponentCount");
        }
        strategyDisagreementScore = percentage(strategyDisagreementScore,
                "strategyDisagreementScore");
        if (maximumStaleDuration == null || maximumStaleDuration.isZero()
                || maximumStaleDuration.isNegative()) {
            throw new MonitoringValidationException(
                    "maximumStaleDuration must be positive");
        }
        if (interval == null) {
            throw new MonitoringValidationException("interval must be configured");
        }
    }

    private static BigDecimal percentage(BigDecimal value, String field) {
        if (value == null) {
            throw new MonitoringValidationException(field + " must be configured");
        }
        if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new MonitoringValidationException(field + " must be between 0 and 100");
        }
        return value;
    }

    private static BigDecimal nonNegative(BigDecimal value, String field) {
        if (value == null) {
            throw new MonitoringValidationException(field + " must be configured");
        }
        if (value.signum() < 0) {
            throw new MonitoringValidationException(field + " must not be negative");
        }
        return value;
    }
}