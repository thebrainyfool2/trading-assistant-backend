package com.tradingassistant.strategy.service;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.Interval;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory staging of the latest candle and indicator values per instrument and
 * interval.
 *
 * <p>This is feed staging, not storage: one candle and one indicator map are
 * retained per key, updates replace previous values, and nothing is persisted.
 * The strategy service decides which of these values are aligned with an
 * assessment timestamp.</p>
 */
public final class InMemoryStrategyMarketDataProvider implements StrategyMarketDataProvider {

    private final ConcurrentHashMap<Key, Candle> candles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Key, Map<String, IndicatorResult>> indicators =
            new ConcurrentHashMap<>();

    public void updateCandle(Candle candle) {
        Objects.requireNonNull(candle, "candle must not be null");
        candles.put(new Key(candle.instrument(), candle.interval()), candle);
    }

    /**
     * Replaces all indicator values of the key with an immutable copy.
     */
    public void updateIndicators(InstrumentKey instrument, Interval interval,
                                 Map<String, IndicatorResult> values) {
        Objects.requireNonNull(instrument, "instrument must not be null");
        Objects.requireNonNull(interval, "interval must not be null");
        Objects.requireNonNull(values, "values must not be null");
        Map<String, IndicatorResult> canonical = new LinkedHashMap<>();
        values.forEach((type, result) -> {
            if (type != null && !type.isBlank() && result != null) {
                canonical.put(type.trim().toUpperCase(Locale.ROOT), result);
            }
        });
        indicators.put(new Key(instrument, interval), Map.copyOf(canonical));
    }

    public void putIndicator(InstrumentKey instrument, Interval interval,
                             IndicatorResult result) {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(instrument, "instrument must not be null");
        Objects.requireNonNull(interval, "interval must not be null");
        indicators.compute(new Key(instrument, interval), (key, existing) -> {
            Map<String, IndicatorResult> updated = new LinkedHashMap<>(
                    existing == null ? Map.of() : existing);
            updated.put(result.indicator().trim().toUpperCase(Locale.ROOT), result);
            return Map.copyOf(updated);
        });
    }

    @Override
    public Optional<Candle> latestCandle(InstrumentKey instrument, Interval interval) {
        if (instrument == null || interval == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(candles.get(new Key(instrument, interval)));
    }

    @Override
    public Map<String, IndicatorResult> latestIndicators(InstrumentKey instrument,
                                                         Interval interval) {
        if (instrument == null || interval == null) {
            return Map.of();
        }
        Map<String, IndicatorResult> values = indicators.get(new Key(instrument, interval));
        return values == null ? Map.of() : values;
    }

    public void remove(InstrumentKey instrument, Interval interval) {
        if (instrument == null || interval == null) {
            return;
        }
        Key key = new Key(instrument, interval);
        candles.remove(key);
        indicators.remove(key);
    }

    public void clear() {
        candles.clear();
        indicators.clear();
    }

    private record Key(InstrumentKey instrument, Interval interval) {
    }
}
