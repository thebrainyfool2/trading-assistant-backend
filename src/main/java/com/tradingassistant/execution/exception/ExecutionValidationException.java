package com.tradingassistant.execution.exception;

/**
 * An execution input, order or configuration was rejected.
 *
 * <p>Every message names the offending field or identifier and never echoes
 * account or credential data.</p>
 */
public class ExecutionValidationException extends ExecutionException {

    public ExecutionValidationException(String message) {
        super(message);
    }
}
