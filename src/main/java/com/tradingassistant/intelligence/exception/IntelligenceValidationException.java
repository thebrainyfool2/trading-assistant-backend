package com.tradingassistant.intelligence.exception;

/**
 * Raised when an intelligence input or configuration is invalid. The same input
 * always produces the same failure, so a rejected assessment is reproducible.
 */
public class IntelligenceValidationException extends IntelligenceException {

    public IntelligenceValidationException(String message) {
        super(message);
    }

    public IntelligenceValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
