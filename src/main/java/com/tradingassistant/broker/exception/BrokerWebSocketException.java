package com.tradingassistant.broker.exception;

/**
 * A market-feed connection or subscription failed. The exception deliberately
 * excludes authentication headers and feed tokens.
 */
public final class BrokerWebSocketException extends BrokerException {

    public BrokerWebSocketException(String message) {
        super(BrokerError.WEBSOCKET, message);
    }

    public BrokerWebSocketException(String message, Throwable cause) {
        super(BrokerError.WEBSOCKET, message, cause);
    }
}
