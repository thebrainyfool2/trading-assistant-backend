package com.tradingassistant.market.service;

import com.tradingassistant.broker.exception.BrokerAuthenticationException;
import com.tradingassistant.broker.exception.BrokerException;
import com.tradingassistant.broker.exception.BrokerSessionExpiredException;
import com.tradingassistant.market.exception.MarketDataAuthenticationException;
import com.tradingassistant.market.exception.MarketDataException;
import com.tradingassistant.market.exception.MarketDataUnavailableException;

/**
 * Keeps broker exception categories from leaking through the application
 * market-data services. Messages are deliberately generic.
 */
final class MarketDataFailureMapper {

    private MarketDataFailureMapper() {
    }

    static MarketDataException translate(BrokerException exception) {
        if (exception instanceof BrokerAuthenticationException
                || exception instanceof BrokerSessionExpiredException) {
            return new MarketDataAuthenticationException(
                    "The market-data session is not available.", exception);
        }
        return new MarketDataUnavailableException(
                "Market data is temporarily unavailable.", exception);
    }
}
