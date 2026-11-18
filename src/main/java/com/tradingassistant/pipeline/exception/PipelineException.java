package com.tradingassistant.pipeline.exception;

/**
 * Root of application-facing runtime pipeline failures. Phase 5 is
 * infrastructure only, so these failures describe wiring, session and
 * aggregation problems rather than trading outcomes.
 */
public class PipelineException extends RuntimeException {

    public PipelineException(String message) {
        super(message);
    }

    public PipelineException(String message, Throwable cause) {
        super(message, cause);
    }
}
