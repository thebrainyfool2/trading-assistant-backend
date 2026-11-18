package com.tradingassistant.pipeline.exception;

/**
 * Raised when creating a session would exceed the configured maximum. The
 * registry never evicts silently, so an operator or a later phase must expire
 * sessions explicitly.
 */
public class SessionLimitExceededException extends PipelineException {

    public SessionLimitExceededException(String message) {
        super(message);
    }
}
