package com.tradingassistant.indicator.validation;

import com.tradingassistant.indicator.IndicatorTestFixtures;
import com.tradingassistant.indicator.calculator.SmaIndicator;
import com.tradingassistant.indicator.config.IndicatorConfig;
import com.tradingassistant.indicator.config.PriceSource;
import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.exception.IndicatorValidationException;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.market.domain.Interval;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IndicatorValidationTest {

    private static final IndicatorConfig CONFIG = IndicatorConfig.period(3, PriceSource.CLOSE);

    @Test
    void rejectsNullEmptyAndInsufficientSeries() {
        assertThrows(IndicatorValidationException.class,
                () -> new SmaIndicator(CONFIG).calculateSeries(null));
        assertThrows(IndicatorValidationException.class,
                () -> new SmaIndicator(CONFIG).calculateSeries(List.of()));
        assertThrows(IndicatorValidationException.class,
                () -> new SmaIndicator(CONFIG).calculateSeries(IndicatorTestFixtures.varying(2)));
    }

    @Test
    void rejectsOutOfOrderAndDuplicateCandles() {
        List<Candle> candles = IndicatorTestFixtures.varying(3);
        assertThrows(IndicatorValidationException.class,
                () -> new SmaIndicator(CONFIG)
                        .calculateSeries(List.of(candles.get(1), candles.get(0), candles.get(2))));
        assertThrows(IndicatorValidationException.class,
                () -> new SmaIndicator(CONFIG)
                        .calculateSeries(List.of(candles.get(0), candles.get(1), candles.get(1))));
    }

    @Test
    void rejectsDuplicateOrOutOfOrderIncrementalUpdates() {
        var duplicate = new SmaIndicator(CONFIG);
        var outOfOrder = new SmaIndicator(CONFIG);
        List<Candle> candles = IndicatorTestFixtures.varying(3);
        duplicate.update(candles.get(0));
        assertThrows(IndicatorValidationException.class, () -> duplicate.update(candles.get(0)));
        outOfOrder.update(candles.get(2));
        assertThrows(IndicatorValidationException.class, () -> outOfOrder.update(candles.get(1)));
    }

    @Test
    void detectsIntervalGapsWithoutRejectingWeekendsOrClosures() {
        List<Candle> candles = IndicatorTestFixtures.varying(3);
        Candle later = IndicatorTestFixtures.candle(3, candles.get(2).close());
        List<Candle> withGap = List.of(candles.get(0), candles.get(1), later);
        var gaps = CandleSeriesValidator.findGaps(withGap);
        assertEquals(1, gaps.size());
        assertEquals(1, gaps.getFirst().missingCandles());
        assertEquals(Interval.ONE_DAY, withGap.getFirst().interval());
    }

    @Test
    void rejectsInvalidParameters() {
        assertThrows(IndicatorValidationException.class,
                () -> IndicatorConfig.period(0, PriceSource.CLOSE));
        assertThrows(IndicatorValidationException.class,
                () -> IndicatorConfig.bollinger(3, BigDecimal.ZERO, PriceSource.CLOSE));
        assertThrows(IndicatorValidationException.class,
                () -> IndicatorConfig.macd(5, 3, 2, PriceSource.CLOSE));
        assertThrows(IndicatorValidationException.class,
                () -> new IndicatorConfig(3, 2, 4, 2, 3,
                        BigDecimal.ONE, BigDecimal.ONE, null));
    }

    @Test
    void priceSourceSelectsTheConfiguredPrice() {
        Candle candle = new Candle(
                IndicatorTestFixtures.KEY,
                Interval.ONE_DAY,
                IndicatorTestFixtures.START,
                new BigDecimal("10"),
                new BigDecimal("14"),
                new BigDecimal("8"),
                new BigDecimal("12"),
                1000L);
        assertEquals(0, PriceSource.OPEN.select(candle).compareTo(new BigDecimal("10")));
        assertEquals(0, PriceSource.HL2.select(candle).compareTo(new BigDecimal("11")));
        assertEquals(0, PriceSource.HLC3.select(candle).compareTo(new BigDecimal("34")
                .divide(new BigDecimal("3"), java.math.MathContext.DECIMAL128)));
        assertEquals(0, PriceSource.OHLC4.select(candle).compareTo(new BigDecimal("11")));
        assertEquals(0, PriceSource.HLCC4.select(candle).compareTo(new BigDecimal("11.5")));
    }

    @Test
    void resultMapsAreImmutable() {
        IndicatorResult result = new SmaIndicator(CONFIG)
                .calculateSeries(IndicatorTestFixtures.varying(3)).latest();
        assertThrows(UnsupportedOperationException.class,
                () -> result.values().put("new", BigDecimal.ONE));
        assertThrows(UnsupportedOperationException.class,
                () -> result.metadata().put("new", "value"));
    }
}
