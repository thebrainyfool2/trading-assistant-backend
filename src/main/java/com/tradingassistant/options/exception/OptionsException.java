package com.tradingassistant.options.exception;

/**
 * Root of application-facing option analytics failures.
 */
public class OptionsException extends RuntimeException {

    public OptionsException(String message) {
        super(message);
    }

    public OptionsException(String message, Throwable cause) {
        super(message, cause);
    }
}
