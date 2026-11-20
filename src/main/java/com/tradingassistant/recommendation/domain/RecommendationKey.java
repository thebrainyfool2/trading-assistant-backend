package com.tradingassistant.recommendation.domain;

import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;

import java.time.Instant;
import java.util.Objects;

/**
 * Identity of one recommendation candidate. The timestamp is the market event
 * timestamp, never a wall-clock instant.
 */
public record RecommendationKey(
        InstrumentKey instrument,
        Interval interval,
        Instant timestamp) {

    public RecommendationKey {
        instrument = Objects.requireNonNull(instrument, "instrument must not be null");
        interval = Objects.requireNonNull(interval, "interval must not be null");
        timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
    }
}
