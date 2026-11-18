package com.tradingassistant.pipeline.aggregation;

import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.pipeline.calendar.MarketCalendar;

import java.util.Objects;

/**
 * Chooses the alignment strategy for an interval and calendar. Daily candles
 * follow the market date; every intraday interval is anchored to the session
 * open.
 */
public final class CandleBucketAligners {

    private CandleBucketAligners() {
    }

    public static CandleBucketAligner forInterval(Interval interval, MarketCalendar calendar) {
        Objects.requireNonNull(interval, "interval must not be null");
        Objects.requireNonNull(calendar, "calendar must not be null");
        if (interval == Interval.ONE_DAY) {
            return new MarketDateAligner(calendar.zone());
        }
        return new TradingSessionAligner(calendar, interval.duration());
    }
}
