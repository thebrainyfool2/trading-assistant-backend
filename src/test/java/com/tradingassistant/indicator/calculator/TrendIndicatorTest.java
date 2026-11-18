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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrendIndicatorTest {

    private static final IndicatorConfig PERIOD_THREE =
            IndicatorConfig.period(3, PriceSource.CLOSE);

    @Test
    void simpleMovingAverageUsesReferenceValues() {
        List<Candle> candles = IndicatorTestFixtures.closes(
                new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("3"),
                new BigDecimal("4"), new BigDecimal("5"), new BigDecimal("6"),
                new BigDecimal("7"), new BigDecimal("8"), new BigDecimal("9"),
                new BigDecimal("10"));

        IndicatorSeries series = new SmaIndicator(PERIOD_THREE).calculateSeries(candles);

        assertEquals(10, series.size());
        assertEquals(IndicatorResult.class, series.results().getFirst().getClass());
        assertTrue(series.results().get(0).warmup());
        assertTrue(series.results().get(1).warmup());
        assertEquals(0, series.results().get(2).requiredValue(IndicatorValues.VALUE)
                .compareTo(new BigDecimal("2")));
        assertEquals(0, series.latest().requiredValue(IndicatorValues.VALUE)
                .compareTo(new BigDecimal("9")));
    }

    @Test
    void exponentialMovingAverageUsesReferenceValues() {
        List<Candle> candles = IndicatorTestFixtures.closes(
                new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("3"),
                new BigDecimal("4"), new BigDecimal("5"));

        IndicatorSeries series = new EmaIndicator(PERIOD_THREE).calculateSeries(candles);

        assertEquals(0, series.results().get(2).requiredValue(IndicatorValues.VALUE)
                .compareTo(new BigDecimal("2.25")));
        assertEquals(0, series.results().get(3).requiredValue(IndicatorValues.VALUE)
                .compareTo(new BigDecimal("3.125")));
        assertEquals(0, series.results().get(4).requiredValue(IndicatorValues.VALUE)
                .compareTo(new BigDecimal("4.0625")));
    }

    @Test
    void weightedMovingAverageUsesReferenceValues() {
        List<Candle> candles = IndicatorTestFixtures.closes(
                new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("3"),
                new BigDecimal("4"), new BigDecimal("5"), new BigDecimal("6"),
                new BigDecimal("7"), new BigDecimal("8"), new BigDecimal("9"),
                new BigDecimal("10"));

        IndicatorSeries series = new WmaIndicator(PERIOD_THREE).calculateSeries(candles);

        BigDecimal expected = new BigDecimal("56")
                .divide(new BigDecimal("6"), java.math.MathContext.DECIMAL128);
        assertEquals(0, series.latest().requiredValue(IndicatorValues.VALUE)
                .compareTo(expected));
    }

    @Test
    void incrementalFeedMatchesHistoricalCalculation() {
        List<Candle> candles = IndicatorTestFixtures.varying(40);
        for (var factory : List.<java.util.function.Function<IndicatorConfig, com.tradingassistant.indicator.api.Indicator>>of(
                SmaIndicator::new, EmaIndicator::new, WmaIndicator::new)) {
            var historical = factory.apply(PERIOD_THREE).calculateSeries(candles);
            var incremental = factory.apply(PERIOD_THREE);
            List<IndicatorResult> results = new ArrayList<>();
            candles.forEach(candle -> results.add(incremental.update(candle)));
            assertEquals(historical.results(), results);
        }
    }

    @Test
    void insufficientHistoryIsRejectedForHistoricalCalculation() {
        assertThrows(IndicatorValidationException.class,
                () -> new SmaIndicator(PERIOD_THREE)
                        .calculateSeries(IndicatorTestFixtures.closes(
                                new BigDecimal("1"), new BigDecimal("2"))));
    }
}
