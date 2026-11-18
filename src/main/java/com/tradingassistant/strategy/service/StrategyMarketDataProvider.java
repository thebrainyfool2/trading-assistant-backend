package com.tradingassistant.strategy.service;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.Interval;

import java.util.Map;
import java.util.Optional;

/**
 * Supplies the current candle and latest indicator values for one instrument
 * and interval.
 *
 * <p>The contract exists so the strategy service never depends on how a feed
 * populates candles or indicators. The service keeps only captures that match
 * the assessed timestamp, so a stale value can never be evaluated as current.</p>
 */
public interface StrategyMarketDataProvider {

    /**
     * Latest completed candle for the instrument and interval, if one exists.
     */
    Optional<Candle> latestCandle(InstrumentKey instrument, Interval interval);

    /**
     * Latest indicator results for the instrument and interval, keyed by type.
     */
    Map<String, IndicatorResult> latestIndicators(InstrumentKey instrument, Interval interval);
}
