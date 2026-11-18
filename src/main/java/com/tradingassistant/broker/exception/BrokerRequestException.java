package com.tradingassistant.broker.exception;

/**
 * A broker request was rejected for a reason other than authentication, session
 * expiry, rate limiting or an invalid response. The broker's raw message is not
 * included; only its error code is retained for diagnostics.
 */
public final class BrokerRequestException extends BrokerException {

    public BrokerRequestException(String message, String brokerCode) {
        super(BrokerError.REQUEST_REJECTED, message, null, brokerCode);
    }

    public BrokerRequestException(String message, String brokerCode, Throwable cause) {
        super(BrokerError.REQUEST_REJECTED, message, cause, brokerCode);
    }
}
