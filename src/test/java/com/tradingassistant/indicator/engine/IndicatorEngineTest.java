package com.tradingassistant.indicator.engine;

import com.tradingassistant.indicator.IndicatorTestFixtures;
import com.tradingassistant.indicator.config.IndicatorConfig;
import com.tradingassistant.indicator.config.PriceSource;
import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.domain.IndicatorSeries;
import com.tradingassistant.indicator.domain.IndicatorValues;
import com.tradingassistant.indicator.exception.IndicatorValidationException;
import com.tradingassistant.market.domain.Candle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndicatorEngineTest {

    private static final IndicatorEngine ENGINE = IndicatorEngine.standard();

    @Test
    void standardRegistryContainsEveryPhaseFourIndicator() {
        assertEquals(Set.of(
                "SMA", "EMA", "WMA", "RSI", "MACD", "STOCH_RSI", "ATR", "BOLLINGER",
                "KELTNER", "DONCHIAN", "ADX", "DMI", "VWAP", "OBV", "CMF", "SUPERTREND"),
                ENGINE.supportedIndicators());
        assertTrue(ENGINE.supports("sma"));
        assertTrue(!ENGINE.supports(null));
        assertThrows(IndicatorValidationException.class, () -> ENGINE.create("UNKNOWN"));
    }

    @Test
    void exposesIncrementalCapabilityAndIdentity() {
        var indicator = ENGINE.create("sma", IndicatorConfig.period(3, PriceSource.CLOSE));
        assertEquals("SMA", indicator.name());
        assertEquals("1.0", indicator.version());
        assertTrue(indicator.supportsIncremental());
        assertEquals(3, indicator.minimumCandles());
    }

    @Test
    void detectsGapsThroughTheEngineFacade() {
        List<Candle> candles = IndicatorTestFixtures.varying(4);
        Candle later = IndicatorTestFixtures.candle(5, candles.getLast().close());
        List<Candle> withGap = List.of(candles.get(0), candles.get(1), later);
        assertEquals(1, ENGINE.detectGaps(withGap).size());
    }

    @Test
    void everyIndicatorCalculatesHistoryAndMatchesIncrementalFeed() {
        List<Candle> candles = IndicatorTestFixtures.varying(120);
        for (Case item : cases()) {
            IndicatorSeries historical = ENGINE.calculateSeries(item.type(), item.config(), candles);
            assertEquals(candles.size(), historical.size(), item.type());
            assertFalse(historical.latest().warmup(), item.type());
            assertTrue(historical.latest().hasValues(), item.type());
            assertTrue(historical.latest().status() == com.tradingassistant.indicator.domain.IndicatorStatus.OK,
                    item.type());

            var incremental = ENGINE.create(item.type(), item.config());
            List<IndicatorResult> results = new ArrayList<>();
            candles.forEach(candle -> results.add(incremental.update(candle)));
            assertEquals(historical.results(), results, item.type());
        }
    }

    @Test
    void calculateLatestReturnsTheLastObservation() {
        List<Candle> candles = IndicatorTestFixtures.varying(30);
        IndicatorResult result = ENGINE.calculateLatest("SMA",
                IndicatorConfig.period(5, PriceSource.CLOSE), candles);
        assertFalse(result.warmup());
        assertEquals(candles.getLast().timestamp(), result.timestamp());
        assertEquals(IndicatorValues.VALUE, result.values().keySet().iterator().next());
    }

    @Test
    void resetClearsRecursiveState() {
        List<Candle> candles = IndicatorTestFixtures.varying(20);
        var indicator = ENGINE.create("EMA", IndicatorConfig.period(3, PriceSource.CLOSE));
        candles.forEach(indicator::update);
        indicator.reset();
        List<IndicatorResult> afterReset = new ArrayList<>();
        candles.forEach(candle -> afterReset.add(indicator.update(candle)));
        var expected = ENGINE.create("EMA", IndicatorConfig.period(3, PriceSource.CLOSE))
                .calculateSeries(candles).results();
        assertEquals(expected, afterReset);
    }

    @Test
    void largeDatasetCompletesWithinAReasonableBound() {
        List<Candle> candles = IndicatorTestFixtures.varying(5_000);
        assertTimeout(Duration.ofSeconds(10), () -> {
            var series = ENGINE.calculateSeries("SMA",
                    IndicatorConfig.period(20, PriceSource.CLOSE), candles);
            assertEquals(5_000, series.size());
            assertFalse(series.latest().warmup());
        });
    }

    private static List<Case> cases() {
        return List.of(
                new Case("SMA", IndicatorConfig.period(3, PriceSource.CLOSE)),
                new Case("EMA", IndicatorConfig.period(3, PriceSource.CLOSE)),
                new Case("WMA", IndicatorConfig.period(3, PriceSource.CLOSE)),
                new Case("RSI", IndicatorConfig.period(3, PriceSource.CLOSE)),
                new Case("MACD", IndicatorConfig.macd(3, 5, 2, PriceSource.CLOSE)),
                new Case("STOCH_RSI", IndicatorConfig.stochasticRsi(3, 2, PriceSource.CLOSE)),
                new Case("ATR", IndicatorConfig.period(3, PriceSource.CLOSE)),
                new Case("BOLLINGER", IndicatorConfig.bollinger(3, BigDecimal.valueOf(2), PriceSource.CLOSE)),
                new Case("KELTNER", IndicatorConfig.keltner(3, 2, BigDecimal.valueOf(2), PriceSource.CLOSE)),
                new Case("DONCHIAN", IndicatorConfig.period(3, PriceSource.CLOSE)),
                new Case("ADX", IndicatorConfig.period(3, PriceSource.CLOSE)),
                new Case("DMI", IndicatorConfig.period(3, PriceSource.CLOSE)),
                new Case("VWAP", IndicatorConfig.defaults()),
                new Case("OBV", IndicatorConfig.defaults()),
                new Case("CMF", IndicatorConfig.period(3, PriceSource.CLOSE)),
                new Case("SUPERTREND", IndicatorConfig.supertrend(3, BigDecimal.valueOf(2), PriceSource.CLOSE)));
    }

    private record Case(String type, IndicatorConfig config) {
    }
}
