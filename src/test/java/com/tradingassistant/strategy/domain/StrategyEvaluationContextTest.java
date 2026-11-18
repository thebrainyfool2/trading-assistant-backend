package com.tradingassistant.strategy.domain;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.strategy.StrategyTestFixtures;
import com.tradingassistant.strategy.exception.StrategyValidationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyEvaluationContextTest {

    @Test
    void derivesTheSessionFromTheMarketAssessment() {
        StrategyEvaluationContext context = StrategyEvaluationContext.of(
                StrategyTestFixtures.marketAssessment());

        assertThat(context.session().summary()).isEqualTo("test session");
        assertThat(context.optionsAssessment()).isEmpty();
        assertThat(context.candle()).isEmpty();
        assertThat(context.indicators()).isEmpty();
    }

    @Test
    void canonicalizesIndicatorKeys() {
        IndicatorResult result = StrategyTestFixtures.indicator("EMA", "value", "100");
        StrategyEvaluationContext context = new StrategyEvaluationContext(
                StrategyTestFixtures.marketAssessment(), Optional.empty(), Optional.empty(),
                Map.of("ema", result));

        assertThat(context.indicators()).containsKey("EMA");
        assertThat(context.indicator("ema")).contains(result);
    }

    @Test
    void rejectsAnOptionsAssessmentOfAnotherInstrument() {
        var options = StrategyTestFixtures.optionsAssessment();
        var foreignMarket = com.tradingassistant.options.OptionsTestFixtures
                .marketAssessment(com.tradingassistant.options.OptionsTestFixtures.OTHER_SPOT,
                        StrategyTestFixtures.START);

        assertThatThrownBy(() -> new StrategyEvaluationContext(foreignMarket,
                Optional.of(options), Optional.empty(), Map.of()))
                .isInstanceOf(StrategyValidationException.class)
                .hasMessageContaining("same instrument");
    }

    @Test
    void rejectsAnOptionsAssessmentWithAnotherTimestamp() {
        var options = StrategyTestFixtures.optionsAssessment();
        var market = StrategyTestFixtures.marketAssessment();
        Instant later = market.timestamp().plusSeconds(60);
        var shiftedMarket = new com.tradingassistant.intelligence.domain.MarketAssessment(
                market.instrument(), market.interval(), later, market.status(), market.warmup(),
                market.confidence(), market.trend(), market.momentum(), market.volatility(),
                market.participation(), market.structure(), market.session());

        assertThatThrownBy(() -> new StrategyEvaluationContext(shiftedMarket,
                Optional.of(options), Optional.empty(), Map.of()))
                .isInstanceOf(StrategyValidationException.class)
                .hasMessageContaining("optionsAssessment timestamp");
    }

    @Test
    void rejectsACandleWithAnotherTimestamp() {
        Candle stale = new Candle(StrategyTestFixtures.SPOT,
                com.tradingassistant.market.domain.Interval.ONE_MINUTE,
                StrategyTestFixtures.START.minusSeconds(60),
                new java.math.BigDecimal("100"), new java.math.BigDecimal("101"),
                new java.math.BigDecimal("99"), new java.math.BigDecimal("100"), 10L);

        assertThatThrownBy(() -> new StrategyEvaluationContext(
                StrategyTestFixtures.marketAssessment(), Optional.empty(), Optional.of(stale),
                Map.of()))
                .isInstanceOf(StrategyValidationException.class)
                .hasMessageContaining("candle timestamp");
    }

    @Test
    void rejectsAnIndicatorFromAnotherTimestamp() {
        IndicatorResult stale = new IndicatorResult("EMA", "1.0",
                StrategyTestFixtures.START.minusSeconds(60),
                com.tradingassistant.indicator.domain.IndicatorStatus.OK, false,
                Map.of("value", new java.math.BigDecimal("100")), Map.of());

        assertThatThrownBy(() -> new StrategyEvaluationContext(
                StrategyTestFixtures.marketAssessment(), Optional.empty(), Optional.empty(),
                Map.of("EMA", stale)))
                .isInstanceOf(StrategyValidationException.class)
                .hasMessageContaining("EMA timestamp");
    }

    @Test
    void rejectsAnIndicatorWhoseKeyDisagreesWithItsType() {
        IndicatorResult result = StrategyTestFixtures.indicator("EMA", "value", "100");

        assertThatThrownBy(() -> new StrategyEvaluationContext(
                StrategyTestFixtures.marketAssessment(), Optional.empty(), Optional.empty(),
                Map.of("RSI", result)))
                .isInstanceOf(StrategyValidationException.class)
                .hasMessageContaining("must match its result type");
    }

    @Test
    void rejectsDuplicateIndicatorKeys() {
        IndicatorResult first = StrategyTestFixtures.indicator("EMA", "value", "100");
        IndicatorResult second = StrategyTestFixtures.indicator("EMA", "value", "101");
        Map<String, IndicatorResult> values = new LinkedHashMap<>();
        values.put("EMA", first);
        values.put("ema", second);

        assertThatThrownBy(() -> new StrategyEvaluationContext(
                StrategyTestFixtures.marketAssessment(), Optional.empty(), Optional.empty(),
                values))
                .isInstanceOf(StrategyValidationException.class)
                .hasMessageContaining("duplicate indicator");
    }

    @Test
    void rejectsBlankIndicatorKeys() {
        assertThatThrownBy(() -> new StrategyEvaluationContext(
                StrategyTestFixtures.marketAssessment(), Optional.empty(), Optional.empty(),
                Map.of(" ", StrategyTestFixtures.indicator("EMA", "value", "100"))))
                .isInstanceOf(StrategyValidationException.class)
                .hasMessageContaining("indicator key");
    }

    @Test
    void rejectsANullMarketAssessment() {
        assertThatThrownBy(() -> new StrategyEvaluationContext(null, Optional.empty(),
                Optional.empty(), Map.of()))
                .isInstanceOf(NullPointerException.class);
    }
}
