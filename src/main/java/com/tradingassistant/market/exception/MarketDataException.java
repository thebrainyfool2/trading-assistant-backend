package com.tradingassistant.market.exception;

/**
 * Base exception for failures in the application market-data layer. Provider
 * exceptions and payloads must be translated before reaching callers.
 */
public class MarketDataException extends RuntimeException {

    public MarketDataException(String message) {
        super(message);
    }

    public MarketDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
