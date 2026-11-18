package com.tradingassistant.qualification.service;

import com.tradingassistant.intelligence.domain.ConfidenceLevel;
import com.tradingassistant.intelligence.domain.MarketAssessment;
import com.tradingassistant.options.domain.OptionsAssessment;
import com.tradingassistant.pipeline.event.MarketAssessmentGeneratedEvent;
import com.tradingassistant.pipeline.event.MarketEvent;
import com.tradingassistant.pipeline.event.MarketEventPublisher;
import com.tradingassistant.pipeline.event.MarketEventType;
import com.tradingassistant.pipeline.event.OptionsAssessmentGeneratedEvent;
import com.tradingassistant.pipeline.event.StrategyEvaluatedEvent;
import com.tradingassistant.pipeline.event.TradeQualifiedEvent;
import com.tradingassistant.qualification.QualificationTestFixtures;
import com.tradingassistant.qualification.config.TradeQualificationConfig;
import com.tradingassistant.qualification.domain.QualificationLedger;
import com.tradingassistant.qualification.domain.QualificationStatus;
import com.tradingassistant.qualification.engine.TradeQualificationEngine;
import com.tradingassistant.qualification.exception.QualificationValidationException;
import com.tradingassistant.qualification.registry.BuiltInQualificationRules;
import com.tradingassistant.qualification.registry.QualificationRuleRegistry;
import com.tradingassistant.strategy.domain.EvaluationStatus;
import com.tradingassistant.strategy.domain.StrategyEvaluation;
import com.tradingassistant.strategy.service.InMemoryStrategyMarketDataProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static com.tradingassistant.qualification.QualificationTestFixtures.LATER;
import static com.tradingassistant.qualification.QualificationTestFixtures.START;
import static com.tradingassistant.qualification.QualificationTestFixtures.STRATEGY_ID;
import static com.tradingassistant.qualification.QualificationTestFixtures.marketAssessment;
import static com.tradingassistant.qualification.QualificationTestFixtures.optionsAssessment;
import static com.tradingassistant.qualification.QualificationTestFixtures.strategyEvaluation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradeQualificationServiceTest {

    private final Clock clock = Clock.fixed(START, ZoneOffset.UTC);
    private final InMemoryStrategyMarketDataProvider provider =
            new InMemoryStrategyMarketDataProvider();
    private final QualificationLedger ledger = new QualificationLedger();
    private RecordingPublisher publisher;

    @Test
    void publishesAQualificationWhenTheAssessmentIsCachedFirst() {
        TradeQualificationService service = service(TradeQualificationConfig.DEFAULT);

        service.onEvent(marketEvent(marketAssessment()));
        service.onEvent(strategyEvent(strategyEvaluation()));

        assertThat(publisher.qualifiedEvents()).hasSize(1);
        TradeQualifiedEvent event = publisher.qualifiedEvents().getFirst();
        assertThat(event.type()).isEqualTo(MarketEventType.TRADE_QUALIFIED);
        assertThat(event.occurredAt()).isEqualTo(START);
        assertThat(event.qualifications()).hasSize(1);
        assertThat(event.qualifications().getFirst().status())
                .isEqualTo(QualificationStatus.QUALIFIED);
        assertThat(event.qualifications().getFirst().strategyId()).isEqualTo(STRATEGY_ID);
    }

    @Test
    void stagesAnEvaluationUntilItsAssessmentArrives() {
        TradeQualificationService service = service(TradeQualificationConfig.DEFAULT);

        service.onEvent(strategyEvent(strategyEvaluation()));

        assertThat(publisher.qualifiedEvents()).isEmpty();
        assertThat(service.pendingEvaluations()).isEqualTo(1);

        service.onEvent(marketEvent(marketAssessment()));

        assertThat(publisher.qualifiedEvents()).hasSize(1);
        assertThat(service.pendingEvaluations()).isZero();
    }

    @Test
    void suppressesARepeatedQualificationOfTheSameInstant() {
        TradeQualificationService service = service(TradeQualificationConfig.DEFAULT);

        service.onEvent(marketEvent(marketAssessment()));
        service.onEvent(strategyEvent(strategyEvaluation()));
        service.onEvent(strategyEvent(strategyEvaluation()));

        assertThat(publisher.qualifiedEvents()).hasSize(1);
        assertThat(service.suppressedDuplicates()).isEqualTo(1);
        assertThat(ledger.size()).isEqualTo(1);
    }

    @Test
    void publishesADisqualifiedResult() {
        TradeQualificationService service = service(TradeQualificationConfig.DEFAULT);

        service.onEvent(marketEvent(marketAssessment(ConfidenceLevel.MEDIUM)));
        service.onEvent(strategyEvent(strategyEvaluation()));

        assertThat(publisher.qualifiedEvents()).hasSize(1);
        assertThat(publisher.qualifiedEvents().getFirst().qualifications().getFirst().status())
                .isEqualTo(QualificationStatus.DISQUALIFIED);
    }

    @Test
    void doesNotSuppressRepeatedEvaluationsThatWereNeverQualified() {
        TradeQualificationService service = service(TradeQualificationConfig.DEFAULT);

        service.onEvent(marketEvent(marketAssessment(ConfidenceLevel.MEDIUM)));
        service.onEvent(strategyEvent(strategyEvaluation()));
        service.onEvent(strategyEvent(strategyEvaluation()));

        assertThat(publisher.qualifiedEvents()).hasSize(2);
        assertThat(service.suppressedDuplicates()).isZero();
        assertThat(ledger.size()).isZero();
    }

    @Test
    void attachesAnAlignedOptionAssessment() {
        TradeQualificationService service = service(TradeQualificationConfig.builder()
                .requireOptions(true)
                .build());

        service.onEvent(marketEvent(marketAssessment()));
        service.onEvent(optionsEvent(optionsAssessment(START)));
        service.onEvent(strategyEvent(strategyEvaluation()));

        assertThat(publisher.qualifiedEvents()).hasSize(1);
        assertThat(publisher.qualifiedEvents().getFirst().qualifications().getFirst().status())
                .isEqualTo(QualificationStatus.QUALIFIED);
    }

    @Test
    void treatsAnOptionAssessmentOfAnotherInstantAsAbsent() {
        TradeQualificationService service = service(TradeQualificationConfig.builder()
                .requireOptions(true)
                .build());

        service.onEvent(marketEvent(marketAssessment()));
        service.onEvent(optionsEvent(optionsAssessment(LATER)));
        service.onEvent(strategyEvent(strategyEvaluation()));

        assertThat(publisher.qualifiedEvents()).hasSize(1);
        TradeQualifiedEvent event = publisher.qualifiedEvents().getFirst();
        assertThat(event.qualifications().getFirst().status())
                .isEqualTo(QualificationStatus.INSUFFICIENT_DATA);
        assertThat(event.qualifications().getFirst().rule(BuiltInQualificationRules.OPTIONS_REQUIRED))
                .get()
                .extracting(rule -> rule.status())
                .isEqualTo(com.tradingassistant.qualification.domain.RuleStatus.INSUFFICIENT_DATA);
    }

    @Test
    void publishesOneEventForABatchOfEvaluations() {
        TradeQualificationService service = service(TradeQualificationConfig.DEFAULT);
        StrategyEvaluation second = QualificationTestFixtures.strategyEvaluation(
                "RSI_PULLBACK", "RSI Pullback", EvaluationStatus.FAILED);

        service.onEvent(marketEvent(marketAssessment()));
        service.onEvent(strategyEvent(strategyEvaluation(), second));

        assertThat(publisher.qualifiedEvents()).hasSize(1);
        assertThat(publisher.qualifiedEvents().getFirst().qualifications()).hasSize(2);
    }

    @Test
    void ignoresEventsItDoesNotOwn() {
        TradeQualificationService service = service(TradeQualificationConfig.DEFAULT);

        service.onEvent(new TradeQualifiedEvent(List.of(), START));

        assertThat(publisher.events()).isEmpty();
    }

    @Test
    void publishesNothingWhenTheEngineIsDisabled() {
        TradeQualificationService service = service(TradeQualificationConfig.builder()
                .enabled(false)
                .build());

        service.onEvent(marketEvent(marketAssessment()));
        service.onEvent(strategyEvent(strategyEvaluation()));

        assertThat(publisher.events()).isEmpty();
        assertThat(service.pendingEvaluations()).isZero();
        assertThat(service.cachedMarketAssessments()).isZero();
    }

    @Test
    void clearCachesDropsStagedEvaluations() {
        TradeQualificationService service = service(TradeQualificationConfig.DEFAULT);
        service.onEvent(strategyEvent(strategyEvaluation()));

        service.clearCaches();

        assertThat(service.pendingEvaluations()).isZero();
        service.onEvent(marketEvent(marketAssessment()));
        assertThat(publisher.events()).isEmpty();
    }

    @Test
    void rejectsANullEvent() {
        TradeQualificationService service = service(TradeQualificationConfig.DEFAULT);

        assertThatThrownBy(() -> service.onEvent(null))
                .isInstanceOf(QualificationValidationException.class)
                .hasMessageContaining("Event");
    }

    private TradeQualificationService service(TradeQualificationConfig config) {
        publisher = new RecordingPublisher();
        provider.updateCandle(QualificationTestFixtures.candle());
        TradeQualificationEngine engine = new TradeQualificationEngine(config,
                new QualificationRuleRegistry(config,
                        BuiltInQualificationRules.all(config, ledger)), Runnable::run);
        return new TradeQualificationService(engine, config, ledger, provider, publisher, clock);
    }

    private static MarketAssessmentGeneratedEvent marketEvent(MarketAssessment assessment) {
        return new MarketAssessmentGeneratedEvent(assessment, START);
    }

    private static OptionsAssessmentGeneratedEvent optionsEvent(OptionsAssessment assessment) {
        return new OptionsAssessmentGeneratedEvent(assessment, START);
    }

    private static StrategyEvaluatedEvent strategyEvent(StrategyEvaluation... evaluations) {
        return new StrategyEvaluatedEvent(List.of(evaluations), START);
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

        List<TradeQualifiedEvent> qualifiedEvents() {
            return events.stream()
                    .filter(TradeQualifiedEvent.class::isInstance)
                    .map(TradeQualifiedEvent.class::cast)
                    .toList();
        }
    }
}
