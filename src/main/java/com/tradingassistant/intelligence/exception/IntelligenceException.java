package com.tradingassistant.intelligence.exception;

/**
 * Root of application-facing market-intelligence failures. Intelligence is a
 * descriptive layer, so its failures are validation failures rather than market
 * outcomes.
 */
public class IntelligenceException extends RuntimeException {

    public IntelligenceException(String message) {
        super(message);
    }

    public IntelligenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
