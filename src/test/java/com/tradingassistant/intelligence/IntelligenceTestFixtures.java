package com.tradingassistant.intelligence;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.domain.IndicatorSeries;
import com.tradingassistant.indicator.domain.IndicatorStatus;
import com.tradingassistant.intelligence.domain.AnalysisContext;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.market.domain.Exchange;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.market.time.MarketStatusResolver;
import com.tradingassistant.pipeline.calendar.MarketCalendar;
import com.tradingassistant.pipeline.calendar.MarketCalendarRegistry;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class IntelligenceTestFixtures {

    public static final InstrumentKey KEY = new InstrumentKey(Segment.NSE_CASH, "3045");
    public static final InstrumentKey OTHER_KEY = new InstrumentKey(Segment.NSE_CASH, "2885");
    public static final Instant START = ist("2026-11-18T09:15:00");

    private IntelligenceTestFixtures() {
    }

    public static Instant ist(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(MarketStatusResolver.MARKET_ZONE).toInstant();
    }

    public static Instant timestamp(int minuteOffset) {
        return START.plus(Duration.ofMinutes(minuteOffset));
    }

    public static MarketCalendar calendar() {
        return MarketCalendarRegistry.defaults().get(Exchange.NSE);
    }

    public static Candle candle(int minuteOffset, String close) {
        BigDecimal value = new BigDecimal(close);
        return candle(minuteOffset, close, value.add(BigDecimal.ONE).toPlainString(),
                value.subtract(BigDecimal.ONE).toPlainString(), close);
    }

    public static Candle candle(
            int minuteOffset,
            String open,
            String high,
            String low,
            String close) {
        return candle(minuteOffset, open, high, low, close, 1_000L);
    }

    public static Candle candle(
            int minuteOffset,
            String open,
            String high,
            String low,
            String close,
            long volume) {
        return new Candle(
                KEY,
                Interval.ONE_MINUTE,
                timestamp(minuteOffset),
                new BigDecimal(open),
                new BigDecimal(high),
                new BigDecimal(low),
                new BigDecimal(close),
                volume);
    }

    public static Candle candleAt(Instant instant, String close) {
        BigDecimal value = new BigDecimal(close);
        return new Candle(
                KEY, Interval.ONE_MINUTE, instant, value,
                value.add(BigDecimal.ONE), value.subtract(BigDecimal.ONE), value, 1_000L);
    }

    /**
     * Rising candles with strictly increasing highs and lows.
     */
    public static List<Candle> risingCandles(int count) {
        List<Candle> candles = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            BigDecimal close = BigDecimal.valueOf(100L + index);
            candles.add(candle(index, close.toPlainString(),
                    close.add(BigDecimal.ONE).toPlainString(),
                    close.subtract(BigDecimal.ONE).toPlainString(),
                    close.toPlainString()));
        }
        return List.copyOf(candles);
    }

    public static IndicatorResult result(String type, Instant timestamp, String key, String value) {
        return result(type, timestamp, Map.of(key, new BigDecimal(value)));
    }

    public static IndicatorResult result(
            String type,
            Instant timestamp,
            Map<String, BigDecimal> values) {
        return new IndicatorResult(type, "1.0", timestamp, IndicatorStatus.OK, false, values, Map.of());
    }

    public static IndicatorResult band(String type, Instant timestamp, String upper, String lower) {
        return result(type, timestamp, Map.of(
                "upper", new BigDecimal(upper),
                "lower", new BigDecimal(lower)));
    }

    public static IndicatorResult warming(String type, Instant timestamp) {
        return new IndicatorResult(
                type, "1.0", timestamp, IndicatorStatus.WARMING_UP, true, Map.of(), Map.of());
    }

    public static IndicatorSeries series(String type, List<IndicatorResult> results) {
        return new IndicatorSeries(type, "1.0", results);
    }

    /**
     * Context built from explicit per-indicator histories.
     */
    public static AnalysisContext context(
            List<Candle> candles,
            Map<String, List<IndicatorResult>> histories) {
        Map<String, IndicatorSeries> series = new LinkedHashMap<>();
        histories.forEach((type, results) -> series.put(type, series(type, results)));
        return new AnalysisContext(KEY, Interval.ONE_MINUTE, candles, series, calendar());
    }

    /**
     * Context whose indicators each have a single observation on the last candle.
     */
    public static AnalysisContext contextWithLatest(
            List<Candle> candles,
            Map<String, IndicatorResult> latest) {
        Map<String, List<IndicatorResult>> histories = new LinkedHashMap<>();
        latest.forEach((type, result) -> histories.put(result.indicator(), List.of(result)));
        return context(candles, histories);
    }

    public static AnalysisContext singleCandleContext(
            String close,
            Map<String, IndicatorResult> latest) {
        return contextWithLatest(List.of(candle(0, close)), latest);
    }
}
