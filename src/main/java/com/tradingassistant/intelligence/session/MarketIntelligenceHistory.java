package com.tradingassistant.intelligence.session;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.domain.IndicatorSeries;
import com.tradingassistant.intelligence.domain.AnalysisContext;
import com.tradingassistant.intelligence.exception.IntelligenceValidationException;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.pipeline.calendar.MarketCalendar;
import com.tradingassistant.pipeline.event.IndicatorUpdatedEvent;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Bounded rolling history for one instrument and interval, fed by
 * {@link IndicatorUpdatedEvent}.
 *
 * <p>The pipeline publishes one candle with all of its indicator results per
 * event; structure needs earlier candles and volatility needs the previous
 * width, so this history keeps the most recent {@code window} observations of
 * each. It performs no analysis and holds no configuration beyond its window.</p>
 *
 * <p>Append is synchronized and strict: a repeated or older candle is rejected
 * rather than silently reordered, and every indicator must be aligned to the
 * appended candle. The returned {@link AnalysisContext} is an immutable snapshot,
 * so a consumer can hold it after the next event arrives.</p>
 */
public final class MarketIntelligenceHistory {

    private final AnalysisKey key;
    private final int window;
    private final ArrayDeque<Candle> candles = new ArrayDeque<>();
    private final Map<String, ArrayDeque<IndicatorResult>> results = new LinkedHashMap<>();

    public MarketIntelligenceHistory(AnalysisKey key, int window) {
        this.key = Objects.requireNonNull(key, "key must not be null");
        if (window < 1) {
            throw new IntelligenceValidationException("History window must be positive");
        }
        this.window = window;
    }

    public AnalysisKey key() {
        return key;
    }

    public int window() {
        return window;
    }

    public synchronized int candleCount() {
        return candles.size();
    }

    public synchronized Set<String> indicatorTypes() {
        return Set.copyOf(results.keySet());
    }

    /**
     * Adds one candle with its indicator results and returns the resulting
     * immutable context.
     */
    public synchronized AnalysisContext append(IndicatorUpdatedEvent event, MarketCalendar calendar) {
        if (event == null) {
            throw new IntelligenceValidationException("Indicator update event must not be null.");
        }
        Objects.requireNonNull(calendar, "calendar must not be null");
        Candle candle = event.candle();
        if (!key.instrument().equals(candle.instrument())) {
            throw new IntelligenceValidationException(
                    "Indicator update belongs to another instrument.");
        }
        if (key.interval() != candle.interval()) {
            throw new IntelligenceValidationException(
                    "Indicator update belongs to another interval.");
        }
        if (!candles.isEmpty()) {
            Instant previous = candles.peekLast().timestamp();
            if (!candle.timestamp().isAfter(previous)) {
                throw new IntelligenceValidationException(
                        "Candle timestamps must increase: " + candle.timestamp()
                                + " is not after " + previous);
            }
        }
        if (event.results().isEmpty()) {
            throw new IntelligenceValidationException(
                    "An indicator update must carry at least one indicator result.");
        }

        candles.addLast(candle);
        trim(candles);

        for (IndicatorResult result : event.results().values()) {
            if (result == null) {
                throw new IntelligenceValidationException("Indicator results must not be null.");
            }
            if (!result.timestamp().equals(candle.timestamp())) {
                throw new IntelligenceValidationException(
                        "Indicator " + result.indicator() + " is not aligned to candle "
                                + candle.timestamp());
            }
            String type = result.indicator().trim().toUpperCase(Locale.ROOT);
            ArrayDeque<IndicatorResult> history = results.computeIfAbsent(type, ignored -> new ArrayDeque<>());
            history.addLast(result);
            trim(history);
        }

        return snapshot(calendar);
    }

    /**
     * Immutable view of the current history. Empty before the first append.
     */
    public synchronized AnalysisContext snapshot(MarketCalendar calendar) {
        Objects.requireNonNull(calendar, "calendar must not be null");
        if (candles.isEmpty()) {
            throw new IntelligenceValidationException("The analysis history is empty.");
        }
        Map<String, IndicatorSeries> series = new LinkedHashMap<>();
        for (Map.Entry<String, ArrayDeque<IndicatorResult>> entry : results.entrySet()) {
            List<IndicatorResult> observations = List.copyOf(entry.getValue());
            IndicatorResult latest = observations.getLast();
            series.put(entry.getKey(), new IndicatorSeries(
                    latest.indicator(), latest.version(), observations));
        }
        return new AnalysisContext(
                key.instrument(), key.interval(), List.copyOf(candles), series, calendar);
    }

    public synchronized void clear() {
        candles.clear();
        results.clear();
    }

    private <T> void trim(ArrayDeque<T> deque) {
        while (deque.size() > window) {
            deque.removeFirst();
        }
    }
}
