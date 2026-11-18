package com.tradingassistant.indicator.exception;

/**
 * Raised when a candle series or indicator parameter is invalid.
 */
public class IndicatorValidationException extends IndicatorException {

    public IndicatorValidationException(String message) {
        super(message);
    }
}
