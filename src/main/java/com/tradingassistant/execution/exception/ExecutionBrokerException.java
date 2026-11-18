package com.tradingassistant.execution.exception;

/**
 * A broker adapter could not complete a call.
 *
 * <p>{@code retryable} tells the engine whether another attempt is meaningful.
 * Adapters decide this from their own transport semantics; the engine only
 * follows the flag and the configured attempt limit.</p>
 */
public class ExecutionBrokerException extends ExecutionException {

    private final boolean retryable;

    public ExecutionBrokerException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public ExecutionBrokerException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
