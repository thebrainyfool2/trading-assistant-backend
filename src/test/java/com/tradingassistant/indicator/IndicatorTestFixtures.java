package com.tradingassistant.indicator;

import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.Segment;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class IndicatorTestFixtures {

    public static final InstrumentKey KEY = new InstrumentKey(Segment.NSE_CASH, "3045");
    public static final Instant START = Instant.parse("2026-01-01T03:45:00Z");

    private IndicatorTestFixtures() {
    }

    public static List<Candle> closes(BigDecimal... closes) {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < closes.length; i++) {
            candles.add(candle(i, closes[i]));
        }
        return candles;
    }

    public static List<Candle> varying(int count) {
        List<Candle> candles = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int close = 100 + ((i * 17) % 31) + (i / 10);
            candles.add(candle(i, BigDecimal.valueOf(close)));
        }
        return candles;
    }

    public static List<Candle> intradayVarying(int count) {
        List<Candle> candles = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int close = 100 + ((i * 7) % 19);
            candles.add(new Candle(
                    KEY,
                    Interval.FIVE_MINUTE,
                    START.plus(Duration.ofMinutes(5L * i)),
                    BigDecimal.valueOf(close - 1),
                    BigDecimal.valueOf(close + 1),
                    BigDecimal.valueOf(close - 2),
                    BigDecimal.valueOf(close),
                    1000L + i * 50L));
        }
        return candles;
    }

    public static Candle candle(int index, BigDecimal close) {
        BigDecimal open = close;
        BigDecimal high = close.add(BigDecimal.ONE);
        BigDecimal low = close.subtract(BigDecimal.ONE);
        return new Candle(
                KEY,
                Interval.ONE_DAY,
                START.plus(Duration.ofDays(index)),
                open,
                high,
                low,
                close,
                1000L + index * 10L);
    }

    public static Candle candleWithVolume(int index, BigDecimal close, long volume) {
        return new Candle(
                KEY,
                Interval.ONE_DAY,
                START.plus(Duration.ofDays(index)),
                close,
                close.add(BigDecimal.ONE),
                close.subtract(BigDecimal.ONE),
                close,
                volume);
    }
}
