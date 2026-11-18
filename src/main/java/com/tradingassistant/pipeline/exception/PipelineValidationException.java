package com.tradingassistant.pipeline.exception;

/**
 * Raised when a pipeline input, subscription or aggregation policy is invalid.
 * Validation failures are programming errors and always deterministic: the
 * same input is rejected with the same message.
 */
public class PipelineValidationException extends PipelineException {

    public PipelineValidationException(String message) {
        super(message);
    }

    public PipelineValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
