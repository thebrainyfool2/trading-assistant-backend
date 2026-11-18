package com.tradingassistant.indicator.exception;

/**
 * Root of application-facing indicator failures.
 */
public class IndicatorException extends RuntimeException {

    public IndicatorException(String message) {
        super(message);
    }

    public IndicatorException(String message, Throwable cause) {
        super(message, cause);
    }
}
