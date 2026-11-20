package com.tradingassistant.monitoring.domain;

/**
 * Deterministic description of how well an active trade's original thesis is
 * holding up against the latest existing engine outputs.
 */
public enum MonitoringState {
    HEALTHY,
    WEAKENING,
    AT_RISK,
    EXIT_RECOMMENDED,
    INVALIDATED
}