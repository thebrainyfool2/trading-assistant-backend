package com.tradingassistant.market.exception;

public class MarketDataMappingException extends MarketDataException {

    public MarketDataMappingException(String message) {
        super(message);
    }

    public MarketDataMappingException(String message, Throwable cause) {
        super(message, cause);
    }
}
