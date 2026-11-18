package com.tradingassistant.market.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One OHLCV candle. Timestamps are UTC instants; the originating interval and
 * instrument are carried with the values so indicators do not need external
 * context to interpret a candle.
 */
public record Candle(
        InstrumentKey instrument,
        Interval interval,
        Instant timestamp,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume
) {

    public Candle {
        instrument = DomainValidation.required(instrument, "instrument");
        interval = DomainValidation.required(interval, "interval");
        timestamp = DomainValidation.required(timestamp, "timestamp");
        open = DomainValidation.nonNegative(DomainValidation.required(open, "open"), "open");
        high = DomainValidation.nonNegative(DomainValidation.required(high, "high"), "high");
        low = DomainValidation.nonNegative(DomainValidation.required(low, "low"), "low");
        close = DomainValidation.nonNegative(DomainValidation.required(close, "close"), "close");
        volume = DomainValidation.nonNegative(volume, "volume");
        if (high.compareTo(low) < 0) {
            throw new IllegalArgumentException("high must not be below low");
        }
        if (high.compareTo(open) < 0 || high.compareTo(close) < 0) {
            throw new IllegalArgumentException("high must not be below open or close");
        }
        if (low.compareTo(open) > 0 || low.compareTo(close) > 0) {
            throw new IllegalArgumentException("low must not be above open or close");
        }
    }
}
