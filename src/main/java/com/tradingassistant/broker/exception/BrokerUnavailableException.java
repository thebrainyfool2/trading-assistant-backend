package com.tradingassistant.broker.exception;

/**
 * The broker could not be reached, is in maintenance, or returned a transient
 * server failure. Callers may retry only when the operation is safe to repeat.
 */
public final class BrokerUnavailableException extends BrokerException {

    public BrokerUnavailableException(String message, Throwable cause) {
        super(BrokerError.UNAVAILABLE, message, cause);
    }

    public BrokerUnavailableException(String message) {
        super(BrokerError.UNAVAILABLE, message);
    }

    public static BrokerUnavailableException network(String message, Throwable cause) {
        return new BrokerUnavailableException(message, cause);
    }
}
