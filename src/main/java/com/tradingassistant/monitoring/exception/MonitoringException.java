package com.tradingassistant.monitoring.exception;

/** Base failure for monitoring use cases. */
public class MonitoringException extends RuntimeException {

    public MonitoringException(String message) {
        super(message);
    }

    public MonitoringException(String message, Throwable cause) {
        super(message, cause);
    }
}