package com.tradingassistant.intelligence.session;

import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;

import java.util.Objects;

/**
 * Identity of one rolling analysis history: an instrument and a candle interval.
 *
 * <p>Indicator configuration is deliberately absent: two indicator
 * configurations that produce the same type on the same instrument and interval
 * would feed one description, and keeping configuration out of the key prevents
 * duplicate histories for what a consumer sees as one market.</p>
 */
public record AnalysisKey(InstrumentKey instrument, Interval interval) {

    public AnalysisKey {
        instrument = Objects.requireNonNull(instrument, "instrument must not be null");
        interval = Objects.requireNonNull(interval, "interval must not be null");
    }
}
