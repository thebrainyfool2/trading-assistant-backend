package com.tradingassistant.market.exception;

public class MarketDataAuthenticationException extends MarketDataException {

    public MarketDataAuthenticationException(String message) {
        super(message);
    }

    public MarketDataAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
