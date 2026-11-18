package com.tradingassistant.pipeline.event;

/**
 * Stable event discriminator for routing, metrics and future asynchronous
 * transport. The value is part of the event contract, so it is explicit rather
 * than derived from a class name.
 */
public enum MarketEventType {
    TICK_RECEIVED,
    CANDLE_COMPLETED,
    INDICATOR_UPDATED,
    SESSION_STARTED,
    SESSION_EXPIRED,
    MARKET_ASSESSMENT_GENERATED,
    OPTIONS_ASSESSMENT_GENERATED,
    STRATEGY_EVALUATED
}
