package com.tradingassistant.qualification.exception;

/**
 * Base type for qualification failures.
 */
public class QualificationException extends RuntimeException {

    public QualificationException(String message) {
        super(message);
    }

    public QualificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
