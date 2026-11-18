package com.tradingassistant.pipeline.event;

/**
 * Receives pipeline events. A listener runs on the publishing thread and must
 * return quickly; blocking work belongs on a consumer's own executor so a slow
 * observer cannot stall candle processing.
 */
@FunctionalInterface
public interface MarketEventListener {

    void onEvent(MarketEvent event);
}
