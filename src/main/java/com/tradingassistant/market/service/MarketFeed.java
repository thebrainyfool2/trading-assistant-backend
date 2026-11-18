package com.tradingassistant.market.service;

/**
 * A live market-feed handle exposed to application code. The provider
 * connection is hidden behind this interface.
 */
public interface MarketFeed extends AutoCloseable {

    boolean isOpen();

    @Override
    void close();
}
