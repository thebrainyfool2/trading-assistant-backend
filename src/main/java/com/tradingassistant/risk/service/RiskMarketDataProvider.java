package com.tradingassistant.risk.service;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;

import java.util.Map;
import java.util.Optional;

/**
 * Supplies the current candle and latest indicator values for one instrument and
 * interval.
 *
 * <p>The contract exists so the risk layer never depends on how a feed or another
 * layer populates candles and indicators. The service keeps only values whose
 * timestamp matches the assessment, so a stale ATR can never size a position.</p>
 */
public interface RiskMarketDataProvider {

    /**
     * Latest completed candle for the instrument and interval, if one exists.
     */
    Optional<Candle> latestCandle(InstrumentKey instrument, Interval interval);

    /**
     * Latest indicator results for the instrument and interval, keyed by type.
     */
    Map<String, IndicatorResult> latestIndicators(InstrumentKey instrument, Interval interval);
}
