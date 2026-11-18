package com.tradingassistant.indicator.exception;

/**
 * Raised when a mathematically undefined or otherwise impossible indicator
 * calculation is requested.
 */
public class IndicatorCalculationException extends IndicatorException {

    public IndicatorCalculationException(String message) {
        super(message);
    }

    public IndicatorCalculationException(String message, Throwable cause) {
        super(message, cause);
    }
}
