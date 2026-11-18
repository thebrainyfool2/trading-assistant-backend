package com.tradingassistant.market.exception;

public class MarketDataUnavailableException extends MarketDataException {

    public MarketDataUnavailableException(String message) {
        super(message);
    }

    public MarketDataUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
