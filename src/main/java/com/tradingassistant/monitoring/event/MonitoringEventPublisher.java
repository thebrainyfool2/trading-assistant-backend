package com.tradingassistant.monitoring.event;

/** Outbound port for monitoring events. */
@FunctionalInterface
public interface MonitoringEventPublisher {

    void publish(MonitoringEvent event);
}