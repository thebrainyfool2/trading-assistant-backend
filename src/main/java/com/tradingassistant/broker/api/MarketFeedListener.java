package com.tradingassistant.broker.api;

import com.tradingassistant.broker.model.FeedTick;

/**
 * Receives market-feed lifecycle and tick events. Implementations must return
 * quickly; blocking work belongs on the application's own executor.
 */
public interface MarketFeedListener {

    default void onConnected() {
    }

    default void onTick(FeedTick tick) {
    }

    default void onDisconnected(int statusCode, String reason) {
    }

    default void onError(Throwable error) {
    }
}
