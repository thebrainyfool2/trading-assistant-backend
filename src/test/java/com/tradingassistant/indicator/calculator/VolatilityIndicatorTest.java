package com.tradingassistant.indicator.calculator;

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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VolatilityIndicatorTest {

    @Test
    void atrUsesWilderAverageOfTrueRanges() {
        List<Candle> candles = IndicatorTestFixtures.closes(
                new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("10"),
                new BigDecimal("10"));
        IndicatorSeries series = new AtrIndicator(
                IndicatorConfig.period(2, PriceSource.CLOSE)).calculateSeries(candles);

        assertEquals(0, series.results().get(2).requiredValue(IndicatorValues.VALUE)
                .compareTo(new BigDecimal("2")));
        assertEquals(0, series.results().get(3).requiredValue(IndicatorValues.VALUE)
                .compareTo(new BigDecimal("2")));
    }

    @Test
    void bollingerBandsCollapseWhenPriceIsFlat() {
        List<Candle> candles = IndicatorTestFixtures.closes(
                new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("10"));
        IndicatorSeries series = new BollingerBandsIndicator(
                IndicatorConfig.bollinger(3, BigDecimal.valueOf(2), PriceSource.CLOSE))
                .calculateSeries(candles);

        IndicatorResult latest = series.latest();
        assertEquals(0, latest.requiredValue(IndicatorValues.MIDDLE).compareTo(BigDecimal.TEN));
        assertEquals(0, latest.requiredValue(IndicatorValues.UPPER).compareTo(BigDecimal.TEN));
        assertEquals(0, latest.requiredValue(IndicatorValues.LOWER).compareTo(BigDecimal.TEN));
    }

    @Test
    void keltnerChannelsUseAtrWidth() {
        List<Candle> candles = IndicatorTestFixtures.closes(
                new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("10"));
        IndicatorSeries series = new KeltnerChannelsIndicator(
                IndicatorConfig.keltner(3, 2, BigDecimal.valueOf(2), PriceSource.CLOSE))
                .calculateSeries(candles);

        IndicatorResult latest = series.latest();
        assertEquals(0, latest.requiredValue(IndicatorValues.MIDDLE).compareTo(BigDecimal.TEN));
        assertEquals(0, latest.requiredValue(IndicatorValues.UPPER).compareTo(new BigDecimal("14")));
        assertEquals(0, latest.requiredValue(IndicatorValues.LOWER).compareTo(new BigDecimal("6")));
    }

    @Test
    void donchianChannelsTrackRollingHighsAndLows() {
        List<Candle> candles = IndicatorTestFixtures.closes(
                new BigDecimal("10"), new BigDecimal("12"), new BigDecimal("11"),
                new BigDecimal("15"), new BigDecimal("14"));
        IndicatorSeries series = new DonchianChannelsIndicator(
                IndicatorConfig.period(3, PriceSource.CLOSE)).calculateSeries(candles);

        IndicatorResult first = series.results().get(2);
        assertEquals(0, first.requiredValue(IndicatorValues.UPPER).compareTo(new BigDecimal("13")));
        assertEquals(0, first.requiredValue(IndicatorValues.LOWER).compareTo(new BigDecimal("9")));
        assertEquals(0, first.requiredValue(IndicatorValues.MIDDLE).compareTo(new BigDecimal("11")));
        assertEquals(0, series.latest().requiredValue(IndicatorValues.UPPER)
                .compareTo(new BigDecimal("16")));
        assertEquals(0, series.latest().requiredValue(IndicatorValues.LOWER)
                .compareTo(new BigDecimal("10")));
    }

    @Test
    void incrementalFeedMatchesHistoricalCalculation() {
        List<Candle> candles = IndicatorTestFixtures.varying(60);
        assertIncremental(AtrIndicator::new, IndicatorConfig.period(3, PriceSource.CLOSE), candles);
        assertIncremental(BollingerBandsIndicator::new,
                IndicatorConfig.bollinger(3, BigDecimal.valueOf(2), PriceSource.CLOSE), candles);
        assertIncremental(KeltnerChannelsIndicator::new,
                IndicatorConfig.keltner(3, 2, BigDecimal.valueOf(2), PriceSource.CLOSE), candles);
        assertIncremental(DonchianChannelsIndicator::new,
                IndicatorConfig.period(3, PriceSource.CLOSE), candles);
    }

    @Test
    void atrHistoricalCalculationRequiresFullWarmup() {
        assertTrue(new AtrIndicator(IndicatorConfig.period(3, PriceSource.CLOSE))
                .minimumCandles() == 4);
        org.junit.jupiter.api.Assertions.assertThrows(IndicatorValidationException.class,
                () -> new AtrIndicator(IndicatorConfig.period(3, PriceSource.CLOSE))
                        .calculateSeries(IndicatorTestFixtures.varying(3)));
    }

    private static void assertIncremental(
            java.util.function.Function<IndicatorConfig, com.tradingassistant.indicator.api.Indicator> factory,
            IndicatorConfig config,
            List<Candle> candles) {
        var historical = factory.apply(config).calculateSeries(candles);
        var incremental = factory.apply(config);
        List<IndicatorResult> results = new ArrayList<>();
        candles.forEach(candle -> results.add(incremental.update(candle)));
        assertEquals(historical.results(), results);
    }
}
