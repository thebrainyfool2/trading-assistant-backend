package com.tradingassistant.strategy.exception;

/**
 * Root of application-facing strategy failures.
 */
public class StrategyException extends RuntimeException {

    public StrategyException(String message) {
        super(message);
    }

    public StrategyException(String message, Throwable cause) {
        super(message, cause);
    }
}
