package com.tradingassistant.options.exception;

/**
 * Raised when option chain input, analytics input or configuration is invalid.
 * The same input always produces the same message, so a rejected chain is
 * reproducible.
 */
public class OptionsValidationException extends OptionsException {

    public OptionsValidationException(String message) {
        super(message);
    }

    public OptionsValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
