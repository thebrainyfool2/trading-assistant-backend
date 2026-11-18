package com.tradingassistant.pipeline.exception;

/**
 * Raised when a caller continues to use a session that the registry has already
 * expired. Expiry is terminal for an instance; a new session must be created.
 */
public class SessionExpiredException extends PipelineException {

    public SessionExpiredException(String message) {
        super(message);
    }
}
