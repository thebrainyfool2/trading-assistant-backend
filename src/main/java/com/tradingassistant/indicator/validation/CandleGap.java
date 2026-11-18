package com.tradingassistant.indicator.validation;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A detected interval gap. Indicators do not invent missing candles; callers
 * can use this information to decide whether a series is suitable.
 */
public record CandleGap(
        Instant previousTimestamp,
        Instant nextTimestamp,
        Duration expectedInterval,
        long missingCandles
) {

    public CandleGap {
        previousTimestamp = Objects.requireNonNull(previousTimestamp, "previousTimestamp");
        nextTimestamp = Objects.requireNonNull(nextTimestamp, "nextTimestamp");
        expectedInterval = Objects.requireNonNull(expectedInterval, "expectedInterval");
        if (missingCandles < 0) {
            throw new IllegalArgumentException("missingCandles must not be negative");
        }
    }
}
