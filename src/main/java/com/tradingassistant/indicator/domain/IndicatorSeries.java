package com.tradingassistant.indicator.domain;

import java.util.List;
import java.util.Objects;

/**
 * Immutable historical calculation result, preserving warm-up rows so callers
 * can align indicator values with the input candles.
 */
public record IndicatorSeries(String indicator, String version, List<IndicatorResult> results) {

    public IndicatorSeries {
        indicator = Objects.requireNonNull(indicator, "indicator must not be null");
        version = Objects.requireNonNull(version, "version must not be null");
        results = results == null ? List.of() : List.copyOf(results);
    }

    public int size() {
        return results.size();
    }

    public boolean isEmpty() {
        return results.isEmpty();
    }

    public IndicatorResult latest() {
        if (results.isEmpty()) {
            throw new IllegalStateException("The indicator series is empty.");
        }
        return results.getLast();
    }
}
