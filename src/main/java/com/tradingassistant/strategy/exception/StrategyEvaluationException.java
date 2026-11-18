package com.tradingassistant.strategy.exception;

/**
 * Raised when evaluation itself cannot complete, for example when fail-fast is
 * configured and a rule throws, or when a parallel evaluation is interrupted.
 */
public class StrategyEvaluationException extends StrategyException {

    public StrategyEvaluationException(String message) {
        super(message);
    }

    public StrategyEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
