package com.tradingassistant.broker.exception;

/**
 * Stable categories for broker failures. The API layer maps these categories to
 * its own public error codes so broker-specific details never leak to clients.
 */
public enum BrokerError {
    CONFIGURATION,
    AUTHENTICATION_FAILED,
    INVALID_TOTP,
    SESSION_EXPIRED,
    REQUEST_REJECTED,
    RATE_LIMITED,
    UNAVAILABLE,
    NETWORK,
    INVALID_RESPONSE,
    WEBSOCKET
}
