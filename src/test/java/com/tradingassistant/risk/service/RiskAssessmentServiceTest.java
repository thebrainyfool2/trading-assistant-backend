package com.tradingassistant.risk.service;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.pipeline.event.MarketAssessmentGeneratedEvent;
import com.tradingassistant.pipeline.event.MarketEvent;
import com.tradingassistant.pipeline.event.MarketEventPublisher;
import com.tradingassistant.pipeline.event.MarketEventType;
import com.tradingassistant.pipeline.event.OptionsAssessmentGeneratedEvent;
import com.tradingassistant.pipeline.event.RiskAssessmentGeneratedEvent;
import com.tradingassistant.pipeline.event.StrategyEvaluatedEvent;
import com.tradingassistant.pipeline.event.TradeQualifiedEvent;
import com.tradingassistant.qualification.domain.TradeQualification;
import com.tradingassistant.risk.RiskTestFixtures;
import com.tradingassistant.risk.config.RiskEngineConfig;
import com.tradingassistant.risk.domain.RiskStatus;
import com.tradingassistant.risk.engine.RiskEngine;
import com.tradingassistant.risk.exception.RiskValidationException;
import com.tradingassistant.risk.registry.BuiltInRiskRules;
import com.tradingassistant.risk.registry.RiskRuleRegistry;
import com.tradingassistant.strategy.domain.EvaluationStatus;
import com.tradingassistant.strategy.domain.StrategyEvaluation;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.tradingassistant.risk.RiskTestFixtures.ATR;
import static com.tradingassistant.risk.RiskTestFixtures.LATER;
import static com.tradingassistant.risk.RiskTestFixtures.START;
import static com.tradingassistant.risk.RiskTestFixtures.atrIndicator;
import static com.tradingassistant.risk.RiskTestFixtures.candle;
import static com.tradingassistant.risk.RiskTestFixtures.indicators;
import static com.tradingassistant.risk.RiskTestFixtures.marketAssessment;
import static com.tradingassistant.risk.RiskTestFixtures.optionsAssessment;
import static com.tradingassistant.risk.RiskTestFixtures.qualification;
import static com.tradingassistant.risk.RiskTestFixtures.strategyEvaluation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiskAssessmentServiceTest {

    private final Clock clock = Clock.fixed(START, ZoneOffset.UTC);
    private final StubProvider provider = new StubProvider();
    private RecordingPublisher publisher;

    @Test
    void publishesAnAssessmentWhenTheInputsAreCachedFirst() {
        RiskAssessmentService service = service(RiskEngineConfig.DEFAULT);

        service.onEvent(new OptionsAssessmentGeneratedEvent(optionsAssessment(), START));
        service.onEvent(marketEvent());
        service.onEvent(strategyEvent(strategyEvaluation()));
        service.onEvent(qualificationEvent(qualification()));

        assertThat(publisher.riskEvents()).hasSize(1);
        RiskAssessmentGeneratedEvent event = publisher.riskEvents().getFirst();
        assertThat(event.type()).isEqualTo(MarketEventType.RISK_ASSESSMENT_GENERATED);
        assertThat(event.assessments()).hasSize(1);
        assertThat(event.assessments().getFirst().status())
                .isEqualTo(RiskStatus.WITHIN_LIMITS);
        assertThat(event.assessments().getFirst().quantity()).isEqualByComparingTo("100");
    }

    @Test
    void stagesAQualificationUntilItsInputsArrive() {
        RiskAssessmentService service = service(RiskEngineConfig.DEFAULT);

        service.onEvent(qualificationEvent(qualification()));

        assertThat(publisher.riskEvents()).isEmpty();
        assertThat(service.pendingQualifications()).isEqualTo(1);

        service.onEvent(marketEvent());
        service.onEvent(strategyEvent(strategyEvaluation()));

        assertThat(publisher.riskEvents()).hasSize(1);
        assertThat(service.pendingQualifications()).isZero();
    }

    @Test
    void publishesOneEventForABatchOfQualifications() {
        RiskAssessmentService service = service(RiskEngineConfig.DEFAULT);
        StrategyEvaluation second = RiskTestFixtures.strategyEvaluation("RSI_PULLBACK",
                EvaluationStatus.PASSED);
        TradeQualification secondQualification = RiskTestFixtures.qualification(
                "RSI_PULLBACK", com.tradingassistant.qualification.domain.QualificationStatus
                        .QUALIFIED);

        service.onEvent(marketEvent());
        service.onEvent(strategyEvent(strategyEvaluation(), second));
        service.onEvent(qualificationEvent(qualification(), secondQualification));

        assertThat(publisher.riskEvents()).hasSize(1);
        assertThat(publisher.riskEvents().getFirst().assessments()).hasSize(2);
    }

    @Test
    void ignoresAnOptionAssessmentOfAnotherInstant() {
        RiskAssessmentService service = service(RiskEngineConfig.DEFAULT);

        service.onEvent(new OptionsAssessmentGeneratedEvent(optionsAssessment(LATER), START));
        service.onEvent(marketEvent());
        service.onEvent(strategyEvent(strategyEvaluation()));
        service.onEvent(qualificationEvent(qualification()));

        assertThat(publisher.riskEvents()).hasSize(1);
        var assessment = publisher.riskEvents().getFirst().assessments().getFirst();
        assertThat(assessment.status()).isEqualTo(RiskStatus.PARTIAL);
        assertThat(assessment.rule(BuiltInRiskRules.MINIMUM_REWARD_RISK)).get()
                .extracting(rule -> rule.status())
                .isEqualTo(com.tradingassistant.risk.domain.RiskRuleStatus.INSUFFICIENT_DATA);
    }

    @Test
    void ignoresIndicatorValuesOfAnotherInstant() {
        RiskAssessmentService service = service(RiskEngineConfig.DEFAULT);
        provider.indicators = indicators(
                new IndicatorResult("ATR", "1.0", LATER,
                        com.tradingassistant.indicator.domain.IndicatorStatus.OK, false,
                        Map.of("value", new java.math.BigDecimal(ATR)), Map.of()));

        service.onEvent(marketEvent());
        service.onEvent(strategyEvent(strategyEvaluation()));
        service.onEvent(qualificationEvent(qualification()));

        assertThat(publisher.riskEvents()).hasSize(1);
        assertThat(publisher.riskEvents().getFirst().assessments().getFirst().status())
                .isEqualTo(RiskStatus.INSUFFICIENT_DATA);
    }

    @Test
    void ignoresEventsItDoesNotOwn() {
        RiskAssessmentService service = service(RiskEngineConfig.DEFAULT);

        service.onEvent(new RiskAssessmentGeneratedEvent(List.of(), START));

        assertThat(publisher.events()).isEmpty();
    }

    @Test
    void publishesNothingWhenTheEngineIsDisabled() {
        RiskAssessmentService service = service(RiskEngineConfig.builder()
                .enabled(false)
                .build());

        service.onEvent(marketEvent());
        service.onEvent(strategyEvent(strategyEvaluation()));
        service.onEvent(qualificationEvent(qualification()));

        assertThat(publisher.events()).isEmpty();
        assertThat(service.pendingQualifications()).isZero();
        assertThat(service.cachedMarketAssessments()).isZero();
    }

    @Test
    void clearCachesDropsStagedQualifications() {
        RiskAssessmentService service = service(RiskEngineConfig.DEFAULT);
        service.onEvent(qualificationEvent(qualification()));

        service.clearCaches();

        assertThat(service.pendingQualifications()).isZero();
        service.onEvent(marketEvent());
        assertThat(publisher.events()).isEmpty();
    }

    @Test
    void rejectsANullEvent() {
        RiskAssessmentService service = service(RiskEngineConfig.DEFAULT);

        assertThatThrownBy(() -> service.onEvent(null))
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("Event");
    }

    private RiskAssessmentService service(RiskEngineConfig config) {
        publisher = new RecordingPublisher();
        RiskEngine engine = new RiskEngine(config,
                new RiskRuleRegistry(BuiltInRiskRules.all()), Runnable::run);
        return new RiskAssessmentService(engine, config, provider, publisher, clock);
    }

    private static MarketAssessmentGeneratedEvent marketEvent() {
        return new MarketAssessmentGeneratedEvent(marketAssessment(), START);
    }

    private static StrategyEvaluatedEvent strategyEvent(StrategyEvaluation... evaluations) {
        return new StrategyEvaluatedEvent(List.of(evaluations), START);
    }

    private static TradeQualifiedEvent qualificationEvent(TradeQualification... qualifications) {
        return new TradeQualifiedEvent(List.of(qualifications), START);
    }

    private static final class StubProvider implements RiskMarketDataProvider {

        private Candle candle = candle();
        private Map<String, IndicatorResult> indicators = indicators(atrIndicator(ATR));

        @Override
        public Optional<Candle> latestCandle(InstrumentKey instrument, Interval interval) {
            return Optional.ofNullable(candle);
        }

        @Override
        public Map<String, IndicatorResult> latestIndicators(InstrumentKey instrument,
                                                             Interval interval) {
            return indicators;
        }
    }

    private static final class RecordingPublisher implements MarketEventPublisher {

        private final List<MarketEvent> events = new ArrayList<>();

        @Override
        public void publish(MarketEvent event) {
            events.add(event);
        }

        List<MarketEvent> events() {
            return List.copyOf(events);
        }

        List<RiskAssessmentGeneratedEvent> riskEvents() {
            return events.stream()
                    .filter(RiskAssessmentGeneratedEvent.class::isInstance)
                    .map(RiskAssessmentGeneratedEvent.class::cast)
                    .toList();
        }
    }
}
