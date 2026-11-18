package com.tradingassistant.broker.exception;

/**
 * The broker session or refresh token is no longer valid. The session manager
 * uses this signal to attempt a fresh login.
 */
public final class BrokerSessionExpiredException extends BrokerException {

    public BrokerSessionExpiredException(String message, String brokerCode) {
        super(BrokerError.SESSION_EXPIRED, message, null, brokerCode);
    }

    public BrokerSessionExpiredException(String message) {
        this(message, null);
    }
}
