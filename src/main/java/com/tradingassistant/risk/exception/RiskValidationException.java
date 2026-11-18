package com.tradingassistant.risk.exception;

/**
 * A risk input, rule or configuration was rejected.
 *
 * <p>Every message names the offending field or identifier and never echoes
 * payload data.</p>
 */
public class RiskValidationException extends RiskException {

    public RiskValidationException(String message) {
        super(message);
    }
}
