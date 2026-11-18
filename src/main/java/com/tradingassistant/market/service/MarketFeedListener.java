package com.tradingassistant.market.service;

import com.tradingassistant.market.domain.Tick;

/**
 * Receives domain market events. Implementations must return quickly; the
 * provider callback thread must not be used for blocking application work.
 */
public interface MarketFeedListener {

    default void onConnected() {
    }

    default void onTick(Tick tick) {
    }

    default void onDisconnected(int statusCode, String reason) {
    }

    default void onError(Throwable error) {
    }
}
