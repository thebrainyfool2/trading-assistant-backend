package com.tradingassistant.pipeline.exception;

/**
 * Raised after an event has been delivered to every subscriber and at least one
 * subscriber threw. Every failure is retained as a suppressed exception so a
 * single broken listener cannot hide the others.
 */
public class EventDeliveryException extends PipelineException {

    public EventDeliveryException(String message) {
        super(message);
    }

    public EventDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
