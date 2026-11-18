package com.tradingassistant.intelligence.domain;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.domain.IndicatorSeries;
import com.tradingassistant.intelligence.exception.IntelligenceValidationException;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.pipeline.calendar.MarketCalendar;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything the analyzers may read: recent completed candles, the indicator
 * results aligned to them, and the calendar that describes the session.
 *
 * <p>Constructing a context is the validation boundary. The constructor rejects
 * null input, an empty candle series, mixed instruments or intervals,
 * non-ascending candles, an empty indicator set, duplicate indicator types
 * (case-insensitively), a series whose indicator name contradicts its key, and
 * any indicator whose latest observation does not belong to the latest candle.
 * An analyzer can therefore assume a coherent context and never needs defensive
 * null checks.</p>
 *
 * <p>Indicator keys are canonicalized to upper case so a producer's naming
 * convention cannot silently hide an indicator from the analysis.</p>
 *
 * @param instrument instrument all candles and indicators belong to
 * @param interval   candle interval all candles and indicators belong to
 * @param candles    completed candles in strictly ascending timestamp order
 * @param indicators indicator type to its full series, including warm-up rows
 * @param calendar   exchange calendar used for session context
 */
public record AnalysisContext(
        InstrumentKey instrument,
        Interval interval,
        List<Candle> candles,
        Map<String, IndicatorSeries> indicators,
        MarketCalendar calendar) {

    public AnalysisContext {
        instrument = Objects.requireNonNull(instrument, "instrument must not be null");
        interval = Objects.requireNonNull(interval, "interval must not be null");
        calendar = Objects.requireNonNull(calendar, "calendar must not be null");
        if (candles == null || candles.isEmpty()) {
            throw new IntelligenceValidationException("At least one candle is required.");
        }
        List<Candle> candleCopy = List.copyOf(candles);
        Instant previous = null;
        for (Candle candle : candleCopy) {
            if (!instrument.equals(candle.instrument())) {
                throw new IntelligenceValidationException(
                        "Candle instrument does not match the analysis instrument.");
            }
            if (interval != candle.interval()) {
                throw new IntelligenceValidationException(
                        "Candle interval does not match the analysis interval.");
            }
            if (previous != null && !candle.timestamp().isAfter(previous)) {
                throw new IntelligenceValidationException(
                        "Candles must be in strictly ascending timestamp order: " + candle.timestamp());
            }
            previous = candle.timestamp();
        }
        candles = candleCopy;

        if (indicators == null || indicators.isEmpty()) {
            throw new IntelligenceValidationException(
                    "At least one indicator series is required; missing indicators are never assumed.");
        }
        Instant latestTimestamp = candleCopy.getLast().timestamp();
        Map<String, IndicatorSeries> canonical = new LinkedHashMap<>();
        for (Map.Entry<String, IndicatorSeries> entry : indicators.entrySet()) {
            String rawType = entry.getKey();
            if (rawType == null || rawType.isBlank()) {
                throw new IntelligenceValidationException("Indicator types must not be blank.");
            }
            String type = rawType.trim().toUpperCase(Locale.ROOT);
            IndicatorSeries series = entry.getValue();
            if (series == null) {
                throw new IntelligenceValidationException(
                        "Indicator series must not be null: " + type);
            }
            if (canonical.containsKey(type)) {
                throw new IntelligenceValidationException("Duplicate indicator type: " + type);
            }
            if (!series.indicator().equalsIgnoreCase(type)) {
                throw new IntelligenceValidationException(
                        "Indicator series " + series.indicator()
                                + " does not match its key " + type);
            }
            if (series.isEmpty()) {
                throw new IntelligenceValidationException(
                        "Indicator series must contain at least one observation: " + type);
            }
            if (!series.latest().timestamp().equals(latestTimestamp)) {
                throw new IntelligenceValidationException(
                        "Indicator " + type + " is not aligned to the latest candle: "
                                + series.latest().timestamp() + " != " + latestTimestamp);
            }
            canonical.put(type, series);
        }
        indicators = Map.copyOf(canonical);
    }

    public Candle latestCandle() {
        return candles.getLast();
    }

    public int candleCount() {
        return candles.size();
    }

    /**
     * Latest observation of every indicator, keyed by canonical type.
     */
    public Map<String, IndicatorResult> latestResults() {
        Map<String, IndicatorResult> latest = new LinkedHashMap<>();
        for (Map.Entry<String, IndicatorSeries> entry : indicators.entrySet()) {
            latest.put(entry.getKey(), entry.getValue().latest());
        }
        return Map.copyOf(latest);
    }

    public Optional<IndicatorSeries> series(String indicatorType) {
        if (indicatorType == null || indicatorType.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(indicators.get(indicatorType.trim().toUpperCase(Locale.ROOT)));
    }
}
