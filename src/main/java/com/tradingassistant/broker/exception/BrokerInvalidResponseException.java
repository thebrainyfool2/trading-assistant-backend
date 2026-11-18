package com.tradingassistant.broker.exception;

/**
 * The broker returned an HTTP response that could not be validated or mapped
 * into the application's broker model.
 */
public final class BrokerInvalidResponseException extends BrokerException {

    public BrokerInvalidResponseException(String message) {
        super(BrokerError.INVALID_RESPONSE, message);
    }

    public BrokerInvalidResponseException(String message, Throwable cause) {
        super(BrokerError.INVALID_RESPONSE, message, cause);
    }
}
