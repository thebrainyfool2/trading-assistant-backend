package com.tradingassistant.pipeline;

import com.tradingassistant.indicator.config.IndicatorConfig;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.pipeline.exception.PipelineValidationException;
import com.tradingassistant.pipeline.session.IndicatorSessionKey;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Registers one instrument and interval with the pipeline, together with the
 * indicator types that should be maintained for it.
 *
 * <p>A subscription is a wiring statement, not a strategy: it says which candles
 * to build and which indicators to advance. It never expresses a view or an
 * action.</p>
 */
public record PipelineSubscription(
        InstrumentKey instrument,
        Interval interval,
        IndicatorConfig config,
        Set<String> indicatorTypes) {

    public PipelineSubscription {
        instrument = Objects.requireNonNull(instrument, "instrument must not be null");
        interval = Objects.requireNonNull(interval, "interval must not be null");
        config = Objects.requireNonNull(config, "config must not be null");
        if (indicatorTypes == null || indicatorTypes.isEmpty()) {
            throw new PipelineValidationException("At least one indicator type is required");
        }
        TreeSet<String> sorted = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String type : indicatorTypes) {
            if (type == null || type.isBlank()) {
                throw new PipelineValidationException("Indicator types must not be blank");
            }
            sorted.add(type.trim());
        }
        Set<String> sortedTypes = Set.copyOf(new LinkedHashSet<>(sorted));
        indicatorTypes = sortedTypes;
    }

    public static PipelineSubscription of(
            InstrumentKey instrument,
            Interval interval,
            IndicatorConfig config,
            String... indicatorTypes) {
        return new PipelineSubscription(
                instrument,
                interval,
                config,
                indicatorTypes == null ? Set.of() : new LinkedHashSet<>(Arrays.asList(indicatorTypes)));
    }

    public IndicatorSessionKey sessionKey() {
        return new IndicatorSessionKey(instrument, interval, config);
    }
}
