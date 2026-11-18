package com.tradingassistant.risk.exception;

/**
 * A risk rule failed unexpectedly, or its failure was propagated by the
 * fail-fast policy.
 */
public class RiskEvaluationException extends RiskException {

    public RiskEvaluationException(String message) {
        super(message);
    }

    public RiskEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
