package com.tradingassistant.monitoring.domain;

/**
 * How monitoring is triggered. Phase 15 supports event-driven monitoring only;
 * no polling loop recalculates indicators.
 */
public enum MonitoringInterval {
    EVENT_DRIVEN
}