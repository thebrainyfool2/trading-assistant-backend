package com.tradingassistant.risk.exception;

/**
 * Base type for risk-assessment failures.
 */
public class RiskException extends RuntimeException {

    public RiskException(String message) {
        super(message);
    }

    public RiskException(String message, Throwable cause) {
        super(message, cause);
    }
}
