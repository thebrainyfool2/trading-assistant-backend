package com.tradingassistant.market.exception;

public class MarketDataValidationException extends MarketDataException {

    public MarketDataValidationException(String message) {
        super(message);
    }

    public MarketDataValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
