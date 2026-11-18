package com.tradingassistant.indicator.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One immutable indicator observation. Values are named rather than positional
 * so multi-line indicators (MACD, Bollinger Bands, DMI, etc.) never expose raw
 * arrays.
 */
public record IndicatorResult(
        String indicator,
        String version,
        Instant timestamp,
        IndicatorStatus status,
        boolean warmup,
        Map<String, BigDecimal> values,
        Map<String, String> metadata
) {

    public IndicatorResult {
        indicator = Objects.requireNonNull(indicator, "indicator must not be null");
        version = Objects.requireNonNull(version, "version must not be null");
        timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        values = values == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(values));
        metadata = metadata == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(metadata));
    }

    public Optional<BigDecimal> value(String key) {
        return Optional.ofNullable(values.get(key));
    }

    public BigDecimal requiredValue(String key) {
        BigDecimal value = values.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Indicator value is not present: " + key);
        }
        return value;
    }

    public boolean hasValues() {
        return !values.isEmpty();
    }
}
