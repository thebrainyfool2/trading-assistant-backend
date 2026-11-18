package com.tradingassistant.broker.api;

/**
 * A live market-feed connection. Closing is idempotent and performs a graceful
 * WebSocket close when the connection is still open.
 */
public interface MarketFeedConnection extends AutoCloseable {

    boolean isOpen();

    @Override
    void close();
}
