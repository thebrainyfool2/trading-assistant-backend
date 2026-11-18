package com.tradingassistant.broker.exception;

/**
 * Authentication with the broker failed. This includes invalid credentials,
 * invalid TOTP values and accounts that are not permitted to use the API.
 */
public final class BrokerAuthenticationException extends BrokerException {

    public BrokerAuthenticationException(BrokerError error, String message, String brokerCode) {
        super(error, message, null, brokerCode);
    }

    public BrokerAuthenticationException(BrokerError error, String message) {
        this(error, message, null);
    }
}
