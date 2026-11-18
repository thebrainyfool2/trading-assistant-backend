package com.tradingassistant.pipeline.aggregation;

import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Mutable in-progress candle. Deliberately package-private: the aggregator is
 * the only owner and callers only ever observe immutable {@link Candle} values.
 */
final class CandleBucket {

    private final Instant start;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private long volume;

    CandleBucket(Instant start, BigDecimal price, long volume) {
        this.start = start;
        this.open = price;
        this.high = price;
        this.low = price;
        this.close = price;
        this.volume = volume;
    }

    Instant start() {
        return start;
    }

    void apply(BigDecimal price, long tradedQuantity) {
        if (price.compareTo(high) > 0) {
            high = price;
        }
        if (price.compareTo(low) < 0) {
            low = price;
        }
        close = price;
        volume += tradedQuantity;
    }

    Candle toCandle(InstrumentKey instrument, Interval interval) {
        return new Candle(instrument, interval, start, open, high, low, close, volume);
    }
}
