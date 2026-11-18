package com.tradingassistant.pipeline.session;

import com.tradingassistant.indicator.config.IndicatorConfig;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Identity of one long-lived indicator session: instrument, interval and
 * indicator configuration.
 *
 * <p>Configuration values are canonicalized so {@code 2} and {@code 2.00}
 * address the same session. Without canonicalization two callers that supplied
 * the same deviation with different scales would silently create two sessions
 * for one candle stream.</p>
 */
public record IndicatorSessionKey(InstrumentKey instrument, Interval interval, IndicatorConfig config) {

    public IndicatorSessionKey {
        instrument = Objects.requireNonNull(instrument, "instrument must not be null");
        interval = Objects.requireNonNull(interval, "interval must not be null");
        config = canonical(Objects.requireNonNull(config, "config must not be null"));
    }

    private static IndicatorConfig canonical(IndicatorConfig config) {
        return new IndicatorConfig(
                config.period(),
                config.fastPeriod(),
                config.slowPeriod(),
                config.signalPeriod(),
                config.smoothing(),
                canonical(config.multiplier()),
                canonical(config.deviation()),
                config.source());
    }

    private static BigDecimal canonical(BigDecimal value) {
        return value.scale() > 0 ? value.stripTrailingZeros() : value;
    }
}
