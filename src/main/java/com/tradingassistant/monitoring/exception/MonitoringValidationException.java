package com.tradingassistant.monitoring.exception;

/** Rejected trade, context or configuration input. */
public class MonitoringValidationException extends MonitoringException {

    public MonitoringValidationException(String message) {
        super(message);
    }

    public MonitoringValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}