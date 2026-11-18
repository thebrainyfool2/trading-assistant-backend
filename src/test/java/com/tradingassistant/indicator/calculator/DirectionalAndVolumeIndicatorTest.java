package com.tradingassistant.indicator.calculator;

import com.tradingassistant.indicator.IndicatorTestFixtures;
import com.tradingassistant.indicator.config.IndicatorConfig;
import com.tradingassistant.indicator.config.PriceSource;
import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.domain.IndicatorSeries;
import com.tradingassistant.indicator.domain.IndicatorValues;
import com.tradingassistant.indicator.util.CandleMath;
import com.tradingassistant.indicator.util.IndicatorMath;
import com.tradingassistant.market.domain.Candle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectionalAndVolumeIndicatorTest {

    @Test
    void dmiAndAdxRespectWarmupAndBoundedOutputs() {
        List<Candle> candles = IndicatorTestFixtures.varying(60);
        IndicatorConfig config = IndicatorConfig.period(3, PriceSource.CLOSE);
        IndicatorSeries dmi = new DmiIndicator(config).calculateSeries(candles);
        IndicatorSeries adx = new AdxIndicator(config).calculateSeries(candles);

        assertTrue(dmi.results().get(0).warmup());
        assertFalse(dmi.latest().warmup());
        assertEquals(2, dmi.latest().values().size());
        assertTrue(dmi.latest().values().containsKey(IndicatorValues.PLUS_DI));
        assertTrue(dmi.latest().values().containsKey(IndicatorValues.MINUS_DI));
        assertTrue(dmi.latest().requiredValue(IndicatorValues.PLUS_DI).signum() >= 0);
        assertTrue(dmi.latest().requiredValue(IndicatorValues.MINUS_DI).signum() >= 0);
        assertFalse(adx.latest().warmup());
        BigDecimal adxValue = adx.latest().requiredValue(IndicatorValues.ADX);
        assertTrue(adxValue.signum() >= 0);
        assertTrue(adxValue.compareTo(BigDecimal.valueOf(100)) <= 0);
    }

    @Test
    void vwapIsVolumeWeightedAcrossAnIntradaySession() {
        List<Candle> candles = IndicatorTestFixtures.intradayVarying(3);
        IndicatorSeries series = new VwapIndicator(IndicatorConfig.defaults()).calculateSeries(candles);

        BigDecimal firstTypical = CandleMath.typicalPrice(candles.get(0));
        assertEquals(0, series.results().get(0).requiredValue(IndicatorValues.VALUE)
                .compareTo(firstTypical));

        BigDecimal expected = BigDecimal.ZERO;
        BigDecimal totalVolume = BigDecimal.ZERO;
        for (Candle candle : candles) {
            BigDecimal volume = BigDecimal.valueOf(candle.volume());
            expected = expected.add(CandleMath.typicalPrice(candle).multiply(volume));
            totalVolume = totalVolume.add(volume);
        }
        assertEquals(0, series.latest().requiredValue(IndicatorValues.VALUE)
                .compareTo(IndicatorMath.divide(expected, totalVolume)));
    }

    @Test
    void obvAddsAndSubtractsDirectionalVolume() {
        List<Candle> candles = List.of(
                IndicatorTestFixtures.candleWithVolume(0, new BigDecimal("10"), 100),
                IndicatorTestFixtures.candleWithVolume(1, new BigDecimal("12"), 200),
                IndicatorTestFixtures.candleWithVolume(2, new BigDecimal("11"), 300),
                IndicatorTestFixtures.candleWithVolume(3, new BigDecimal("13"), 400));
        IndicatorSeries series = new ObvIndicator(IndicatorConfig.defaults()).calculateSeries(candles);

        assertEquals(0, series.results().get(0).requiredValue(IndicatorValues.VALUE).signum());
        assertEquals(0, series.results().get(1).requiredValue(IndicatorValues.VALUE)
                .compareTo(new BigDecimal("200")));
        assertEquals(0, series.results().get(2).requiredValue(IndicatorValues.VALUE)
                .compareTo(new BigDecimal("-100")));
        assertEquals(0, series.latest().requiredValue(IndicatorValues.VALUE)
                .compareTo(new BigDecimal("300")));
    }

    @Test
    void cmfCollapsesToZeroWhenMoneyFlowMultiplierIsZero() {
        List<Candle> candles = IndicatorTestFixtures.closes(
                new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("10"));
        IndicatorSeries series = new CmfIndicator(
                IndicatorConfig.period(3, PriceSource.CLOSE)).calculateSeries(candles);

        assertEquals(0, series.latest().requiredValue(IndicatorValues.VALUE).signum());
    }

    @Test
    void supertrendProducesDirectionalStateAndMatchesIncrementalFeed() {
        List<Candle> candles = IndicatorTestFixtures.varying(60);
        IndicatorConfig config = IndicatorConfig.supertrend(3, BigDecimal.valueOf(2), PriceSource.CLOSE);
        IndicatorSeries historical = new SupertrendIndicator(config).calculateSeries(candles);
        assertTrue(historical.results().getFirst().warmup());
        assertFalse(historical.latest().warmup());
        BigDecimal direction = historical.latest().requiredValue(IndicatorValues.DIRECTION);
        assertTrue(direction.compareTo(BigDecimal.ONE) == 0
                || direction.compareTo(BigDecimal.ONE.negate()) == 0);

        var incremental = new SupertrendIndicator(config);
        List<IndicatorResult> results = new ArrayList<>();
        candles.forEach(candle -> results.add(incremental.update(candle)));
        assertEquals(historical.results(), results);
    }

    @Test
    void directionalIndicatorsMatchIncrementalFeed() {
        List<Candle> candles = IndicatorTestFixtures.varying(60);
        assertIncremental(DmiIndicator::new,
                IndicatorConfig.period(3, PriceSource.CLOSE), candles);
        assertIncremental(AdxIndicator::new,
                IndicatorConfig.period(3, PriceSource.CLOSE), candles);
        assertIncremental(VwapIndicator::new, IndicatorConfig.defaults(), candles);
        assertIncremental(ObvIndicator::new, IndicatorConfig.defaults(), candles);
        assertIncremental(CmfIndicator::new,
                IndicatorConfig.period(3, PriceSource.CLOSE), candles);
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
