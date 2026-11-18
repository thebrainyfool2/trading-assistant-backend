package com.tradingassistant.strategy.exception;

/**
 * Raised when a strategy definition, context, registry operation or engine
 * configuration is invalid. Messages name the offending field, and the same
 * input always produces the same message.
 */
public class StrategyValidationException extends StrategyException {

    public StrategyValidationException(String message) {
        super(message);
    }

    public StrategyValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
