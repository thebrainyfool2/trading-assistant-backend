package com.tradingassistant.pipeline;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.market.domain.Candle;

import java.util.Map;

/**
 * Receives completed candles and their indicator snapshot before the pipeline
 * publishes the corresponding events.
 *
 * <p>This ordering is important: evaluation stages evaluate a market assessment
 * synchronously when the indicator event is published, so their candle/indicator
 * staging must already be current. Observers are deliberately part of the
 * pipeline boundary rather than another event-bus subscriber.</p>
 */
public interface MarketDataObserver {

    default void onCandle(Candle candle) {
    }

    default void onIndicators(Candle candle, Map<String, IndicatorResult> indicators) {
    }
}