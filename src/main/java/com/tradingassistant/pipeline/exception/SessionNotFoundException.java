package com.tradingassistant.pipeline.exception;

/**
 * Raised when an indicator session is requested by an operation that requires
 * it to exist. Lookups that tolerate absence use {@code Optional} instead.
 */
public class SessionNotFoundException extends PipelineException {

    public SessionNotFoundException(String message) {
        super(message);
    }
}
