package com.tradingassistant.qualification.exception;

/**
 * A qualification rule failed unexpectedly, or its failure was propagated by the
 * fail-fast policy.
 */
public class QualificationEvaluationException extends QualificationException {

    public QualificationEvaluationException(String message) {
        super(message);
    }

    public QualificationEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
