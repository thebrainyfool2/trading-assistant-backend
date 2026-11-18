package com.tradingassistant.indicator.calculator;

import com.tradingassistant.indicator.IndicatorTestFixtures;
import com.tradingassistant.indicator.api.Indicator;
import com.tradingassistant.indicator.config.IndicatorConfig;
import com.tradingassistant.indicator.config.PriceSource;
import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.domain.IndicatorSeries;
import com.tradingassistant.indicator.domain.IndicatorValues;
import com.tradingassistant.market.domain.Candle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MomentumIndicatorTest {

    @Test
    void rsiMatchesHandCalculatedWilderReference() {
        List<Candle> candles = IndicatorTestFixtures.closes(
                new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("3"),
                new BigDecimal("2"), new BigDecimal("4"), new BigDecimal("5"));

        IndicatorSeries series = new RsiIndicator(
                IndicatorConfig.period(2, PriceSource.CLOSE)).calculateSeries(candles);

        assertEquals(0, series.results().get(2).requiredValue(IndicatorValues.VALUE)
                .compareTo(new BigDecimal("100")));
        assertEquals(0, series.results().get(3).requiredValue(IndicatorValues.VALUE)
                .compareTo(new BigDecimal("50")));
        assertEquals(0, series.results().get(4).requiredValue(IndicatorValues.VALUE)
                .compareTo(new BigDecimal("500").divide(new BigDecimal("6"), MathContext.DECIMAL128)));
    }

    @Test
    void rsiMatchesWilderReferenceDataset() {
        List<Candle> candles = IndicatorTestFixtures.closes(
                new BigDecimal("44.34"), new BigDecimal("44.09"), new BigDecimal("44.15"),
                new BigDecimal("43.61"), new BigDecimal("44.33"), new BigDecimal("44.83"),
                new BigDecimal("45.10"), new BigDecimal("45.42"), new BigDecimal("45.84"),
                new BigDecimal("46.08"), new BigDecimal("45.89"), new BigDecimal("46.03"),
                new BigDecimal("45.61"), new BigDecimal("46.28"), new BigDecimal("46.28"));
        BigDecimal actual = new RsiIndicator(IndicatorConfig.period(14, PriceSource.CLOSE))
                .calculate(candles).requiredValue(IndicatorValues.VALUE);
        // Wilder's published reference result for this dataset is approximately 70.46.
        assertTrue(actual.subtract(new BigDecimal("70.46")).abs()
                .compareTo(new BigDecimal("0.01")) < 0);
    }

    @Test
    void macdIsZeroForFlatPricesAfterWarmup() {
        List<Candle> candles = IndicatorTestFixtures.closes(
                new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("10"),
                new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("10"));

        IndicatorSeries series = new MacdIndicator(
                IndicatorConfig.macd(2, 3, 2, PriceSource.CLOSE)).calculateSeries(candles);

        for (int i = 0; i < 3; i++) {
            assertTrue(series.results().get(i).warmup());
        }
        assertFalse(series.results().get(3).warmup());
        assertEquals(0, series.results().get(3).requiredValue(IndicatorValues.MACD).signum());
        assertEquals(0, series.results().get(3).requiredValue(IndicatorValues.SIGNAL).signum());
        assertEquals(0, series.results().get(3).requiredValue(IndicatorValues.HISTOGRAM).signum());
    }

    @Test
    void stochasticRsiProducesBoundedLinesAndWarmupRows() {
        List<Candle> candles = IndicatorTestFixtures.varying(60);
        IndicatorSeries series = new StochasticRsiIndicator(
                IndicatorConfig.stochasticRsi(3, 2, PriceSource.CLOSE)).calculateSeries(candles);

        assertTrue(series.results().get(0).warmup());
        IndicatorResult latest = series.latest();
        assertFalse(latest.warmup());
        BigDecimal k = latest.requiredValue(IndicatorValues.PERCENT_K);
        BigDecimal d = latest.requiredValue(IndicatorValues.PERCENT_D);
        assertTrue(k.signum() >= 0 && k.compareTo(BigDecimal.valueOf(100)) <= 0);
        assertTrue(d.signum() >= 0 && d.compareTo(BigDecimal.valueOf(100)) <= 0);
    }

    @Test
    void incrementalFeedMatchesHistoricalCalculation() {
        List<Candle> candles = IndicatorTestFixtures.varying(60);
        assertIncremental(RsiIndicator::new, IndicatorConfig.period(3, PriceSource.CLOSE), candles);
        assertIncremental(MacdIndicator::new, IndicatorConfig.macd(3, 5, 2, PriceSource.CLOSE), candles);
        assertIncremental(StochasticRsiIndicator::new,
                IndicatorConfig.stochasticRsi(3, 2, PriceSource.CLOSE), candles);
    }

    private static void assertIncremental(
            Function<IndicatorConfig, Indicator> factory,
            IndicatorConfig config,
            List<Candle> candles) {
        var historical = factory.apply(config).calculateSeries(candles);
        var incremental = factory.apply(config);
        List<IndicatorResult> results = new ArrayList<>();
        candles.forEach(candle -> results.add(incremental.update(candle)));
        assertEquals(historical.results(), results);
    }
}
