package com.tradingassistant.strategy.service;

import com.tradingassistant.intelligence.domain.MarketAssessment;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.options.OptionsTestFixtures;
import com.tradingassistant.pipeline.event.MarketAssessmentGeneratedEvent;
import com.tradingassistant.pipeline.event.MarketEvent;
import com.tradingassistant.pipeline.event.MarketEventType;
import com.tradingassistant.pipeline.event.OptionsAssessmentGeneratedEvent;
import com.tradingassistant.pipeline.event.StrategyEvaluatedEvent;
import com.tradingassistant.strategy.StrategyTestFixtures;
import com.tradingassistant.strategy.config.StrategyEngineConfig;
import com.tradingassistant.strategy.domain.EvaluationStatus;
import com.tradingassistant.strategy.domain.StrategyEvaluation;
import com.tradingassistant.strategy.engine.StrategyEvaluationEngine;
import com.tradingassistant.strategy.exception.StrategyValidationException;
import com.tradingassistant.strategy.registry.BuiltInStrategies;
import com.tradingassistant.strategy.registry.StrategyRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyEvaluationServiceTest {

    private final StrategyEngineConfig config = StrategyEngineConfig.DEFAULT;
    private InMemoryStrategyMarketDataProvider provider;
    private StrategyRegistry registry;
    private List<MarketEvent> published;
    private StrategyEvaluationService service;

    @BeforeEach
    void setUp() {
        provider = new InMemoryStrategyMarketDataProvider();
        provider.updateCandle(StrategyTestFixtures.candle("105", 200_000L));
        provider.updateIndicators(StrategyTestFixtures.SPOT, Interval.ONE_MINUTE,
                Map.of("EMA", StrategyTestFixtures.indicator("EMA", "value", "100")));
        registry = new StrategyRegistry(config,
                List.of(BuiltInStrategies.emaTrend(), BuiltInStrategies.pcrExtreme()));
        published = new ArrayList<>();
        service = new StrategyEvaluationService(
                new StrategyEvaluationEngine(config, Runnable::run), registry, config,
                provider, published::add, Clock.fixed(StrategyTestFixtures.START,
                ZoneOffset.UTC));
    }

    private MarketAssessment marketAt(java.time.Instant timestamp) {
        return OptionsTestFixtures.marketAssessment(StrategyTestFixtures.SPOT, timestamp);
    }

    private void publishMarket(MarketAssessment assessment) {
        service.onEvent(new MarketAssessmentGeneratedEvent(assessment,
                assessment.timestamp()));
    }

    private void publishOptions(java.time.Instant timestamp) {
        service.onEvent(new OptionsAssessmentGeneratedEvent(
                StrategyTestFixtures.optionsAssessment(), timestamp));
    }

    @Test
    void publishesEvaluationsWhenTheMarketIsAssessed() {
        publishMarket(StrategyTestFixtures.marketAssessment());

        assertThat(published).hasSize(1);
        StrategyEvaluatedEvent event = (StrategyEvaluatedEvent) published.get(0);
        assertThat(event.type()).isEqualTo(MarketEventType.STRATEGY_EVALUATED);
        assertThat(event.occurredAt()).isEqualTo(StrategyTestFixtures.START);
        assertThat(event.evaluations()).hasSize(2);
        assertThat(event.evaluations()).extracting(StrategyEvaluation::strategyId)
                .containsExactly("EMA_TREND", "PCR_EXTREME");
        assertThat(event.evaluations()).filteredOn(evaluation -> evaluation.strategyId()
                        .equals("EMA_TREND"))
                .singleElement()
                .satisfies(evaluation -> assertThat(evaluation.status())
                        .isEqualTo(EvaluationStatus.PASSED));
    }

    @Test
    void publishesAgainWhenAnAlignedOptionsAssessmentArrives() {
        publishMarket(StrategyTestFixtures.marketAssessment());
        publishOptions(StrategyTestFixtures.START);

        assertThat(published).hasSize(2);
        StrategyEvaluatedEvent second = (StrategyEvaluatedEvent) published.get(1);
        assertThat(second.evaluations()).filteredOn(evaluation -> evaluation.strategyId()
                        .equals("PCR_EXTREME"))
                .singleElement()
                .satisfies(evaluation -> assertThat(evaluation.status())
                        .isEqualTo(EvaluationStatus.PASSED));
    }

    @Test
    void ignoresAnOptionsAssessmentWithoutAMarketAssessmentOfTheSameInstant() {
        publishOptions(StrategyTestFixtures.START);

        assertThat(published).isEmpty();
        assertThat(service.cachedOptionsAssessments()).isEqualTo(1);
    }

    @Test
    void ignoresAnOptionsAssessmentThatDoesNotMatchTheCachedMarketInstant() {
        publishMarket(marketAt(StrategyTestFixtures.START.plusSeconds(60)));

        publishOptions(StrategyTestFixtures.START);

        assertThat(published).hasSize(1);
        assertThat(service.cachedMarketAssessments()).isEqualTo(1);
    }

    @Test
    void ignoresEventsOtherThanAssessments() {
        publishMarket(StrategyTestFixtures.marketAssessment());
        MarketEvent unrelated = new StrategyEvaluatedEvent(List.of(),
                StrategyTestFixtures.START);

        service.onEvent(unrelated);

        assertThat(published).hasSize(1);
    }

    @Test
    void doesNothingWhenTheEngineIsDisabled() {
        StrategyEngineConfig disabled = StrategyEngineConfig.builder().enabled(false).build();
        StrategyEvaluationService inactive = new StrategyEvaluationService(
                new StrategyEvaluationEngine(disabled, Runnable::run), registry, disabled,
                provider, published::add, Clock.fixed(StrategyTestFixtures.START,
                ZoneOffset.UTC));

        inactive.onEvent(new MarketAssessmentGeneratedEvent(
                StrategyTestFixtures.marketAssessment(), StrategyTestFixtures.START));

        assertThat(published).isEmpty();
        assertThat(inactive.cachedMarketAssessments()).isZero();
    }

    @Test
    void publishesNothingWithoutEnabledStrategies() {
        StrategyRegistry disabledOnly = new StrategyRegistry(config,
                List.of(BuiltInStrategies.emaTrend().withEnabled(false)));
        StrategyEvaluationService noStrategies = new StrategyEvaluationService(
                new StrategyEvaluationEngine(config, Runnable::run), disabledOnly, config,
                provider, published::add, Clock.fixed(StrategyTestFixtures.START,
                ZoneOffset.UTC));

        noStrategies.onEvent(new MarketAssessmentGeneratedEvent(
                StrategyTestFixtures.marketAssessment(), StrategyTestFixtures.START));

        assertThat(published).isEmpty();
        assertThat(disabledOnly.enabled()).isEmpty();
        assertThat(disabledOnly.disabled()).hasSize(1);
    }

    @Test
    void isolatesInstruments() {
        publishMarket(StrategyTestFixtures.marketAssessment());
        publishMarket(OptionsTestFixtures.marketAssessment(OptionsTestFixtures.OTHER_SPOT,
                StrategyTestFixtures.START));

        assertThat(published).hasSize(2);
        StrategyEvaluatedEvent second = (StrategyEvaluatedEvent) published.get(1);
        assertThat(second.evaluations()).isNotEmpty()
                .allSatisfy(evaluation -> assertThat(evaluation.instrument())
                        .isEqualTo(OptionsTestFixtures.OTHER_SPOT));
    }

    @Test
    void clearsItsCachesOnDemand() {
        publishMarket(StrategyTestFixtures.marketAssessment());
        publishOptions(StrategyTestFixtures.START);

        assertThat(service.cachedMarketAssessments()).isEqualTo(1);
        assertThat(service.cachedOptionsAssessments()).isEqualTo(1);

        service.clearCaches();

        assertThat(service.cachedMarketAssessments()).isZero();
        assertThat(service.cachedOptionsAssessments()).isZero();
    }

    @Test
    void ignoresCandlesAndIndicatorsOfAnotherTimestamp() {
        provider.updateCandle(new com.tradingassistant.market.domain.Candle(
                StrategyTestFixtures.SPOT, Interval.ONE_MINUTE,
                StrategyTestFixtures.START.minusSeconds(60),
                new java.math.BigDecimal("105"), new java.math.BigDecimal("106"),
                new java.math.BigDecimal("104"), new java.math.BigDecimal("105"), 200_000L));

        publishMarket(StrategyTestFixtures.marketAssessment());

        StrategyEvaluatedEvent event = (StrategyEvaluatedEvent) published.get(0);
        StrategyEvaluation ema = event.evaluations().stream()
                .filter(evaluation -> evaluation.strategyId().equals("EMA_TREND"))
                .findFirst()
                .orElseThrow();
        assertThat(ema.status()).isEqualTo(EvaluationStatus.INSUFFICIENT_DATA);
    }

    @Test
    void rejectsANullEvent() {
        assertThatThrownBy(() -> service.onEvent(null))
                .isInstanceOf(StrategyValidationException.class)
                .hasMessageContaining("Event must not be null");
    }
}
