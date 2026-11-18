package com.tradingassistant.qualification.exception;

/**
 * A qualification input, rule or configuration was rejected.
 *
 * <p>Every message names the offending field or identifier and never echoes
 * payload data.</p>
 */
public class QualificationValidationException extends QualificationException {

    public QualificationValidationException(String message) {
        super(message);
    }
}
