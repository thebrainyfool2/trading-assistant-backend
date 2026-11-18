package com.tradingassistant.market.domain;

import java.time.Duration;

/**
 * Candle intervals supported by the application. The broker adapter maps these
 * values to its own interval identifiers.
 */
public enum Interval {
    ONE_MINUTE(Duration.ofMinutes(1)),
    THREE_MINUTE(Duration.ofMinutes(3)),
    FIVE_MINUTE(Duration.ofMinutes(5)),
    TEN_MINUTE(Duration.ofMinutes(10)),
    FIFTEEN_MINUTE(Duration.ofMinutes(15)),
    THIRTY_MINUTE(Duration.ofMinutes(30)),
    ONE_HOUR(Duration.ofHours(1)),
    ONE_DAY(Duration.ofDays(1));

    private final Duration duration;

    Interval(Duration duration) {
        this.duration = duration;
    }

    public Duration duration() {
        return duration;
    }
}
