package com.tradingassistant.indicator.api;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.domain.IndicatorSeries;
import com.tradingassistant.market.domain.Candle;

import java.util.List;

/**
 * A stateful, reusable technical indicator. Each instance owns one calculation
 * state and must not be shared between concurrent candle streams.
 */
public interface Indicator {

    String name();

    String version();

    int minimumCandles();

    boolean supportsIncremental();

    /**
     * Calculates every input candle from a clean state. Warm-up observations are
     * retained in the returned series.
     */
    IndicatorSeries calculateSeries(List<Candle> candles);

    /**
     * Returns the latest observation after a full historical calculation.
     */
    default IndicatorResult calculate(List<Candle> candles) {
        return calculateSeries(candles).latest();
    }

    /**
     * Advances the indicator by one candle. The caller must feed candles in
     * strictly ascending timestamp order.
     */
    IndicatorResult update(Candle candle);

    void reset();
}
