package com.tradingassistant.broker.exception;

/**
 * Base exception for broker integration failures. Messages are deliberately
 * safe for operational use and must never contain credentials, tokens, OTPs or
 * raw broker response bodies.
 */
public class BrokerException extends RuntimeException {

    private final BrokerError error;
    private final String brokerCode;

    protected BrokerException(BrokerError error, String message) {
        this(error, message, null, null);
    }

    protected BrokerException(BrokerError error, String message, Throwable cause) {
        this(error, message, cause, null);
    }

    protected BrokerException(BrokerError error, String message, Throwable cause, String brokerCode) {
        super(message, cause);
        this.error = error;
        this.brokerCode = brokerCode;
    }

    public BrokerError error() {
        return error;
    }

    /**
     * Broker error code when one was supplied. This is safe to log but is not
     * exposed through API responses.
     */
    public String brokerCode() {
        return brokerCode;
    }
}
