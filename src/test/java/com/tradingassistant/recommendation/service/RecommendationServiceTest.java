package com.tradingassistant.recommendation.service;

import com.tradingassistant.options.service.OptionChainProvider;
import com.tradingassistant.pipeline.event.MarketAssessmentGeneratedEvent;
import com.tradingassistant.pipeline.event.OptionsAssessmentGeneratedEvent;
import com.tradingassistant.pipeline.event.RiskAssessmentGeneratedEvent;
import com.tradingassistant.pipeline.event.StrategyEvaluatedEvent;
import com.tradingassistant.pipeline.event.TradeQualifiedEvent;
import com.tradingassistant.platform.readmodel.MarketDataFreshnessService;
import com.tradingassistant.recommendation.RecommendationTestFixtures;
import com.tradingassistant.recommendation.config.RecommendationConfig;
import com.tradingassistant.recommendation.domain.RecommendationKey;
import com.tradingassistant.recommendation.domain.RecommendationType;
import com.tradingassistant.recommendation.engine.RecommendationEngine;
import com.tradingassistant.recommendation.event.RecommendationEvent;
import com.tradingassistant.recommendation.event.RecommendationEventBus;
import com.tradingassistant.recommendation.event.RecommendationExpiredEvent;
import com.tradingassistant.recommendation.event.RecommendationGeneratedEvent;
import com.tradingassistant.recommendation.event.RecommendationUpdatedEvent;
import com.tradingassistant.recommendation.registry.ScorerRegistry;
import com.tradingassistant.strategy.service.StrategyMarketDataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecommendationServiceTest {

    private final OptionChainProvider chainProvider = mock(OptionChainProvider.class);
    private final StrategyMarketDataProvider marketDataProvider =
            mock(StrategyMarketDataProvider.class);
    private final MarketDataFreshnessService freshnessService =
            mock(MarketDataFreshnessService.class);
    private final RecommendationEventBus eventBus = new RecommendationEventBus();
    private final List<RecommendationEvent> received = new ArrayList<>();
    private RecommendationService service;

    @BeforeEach
    void setUp() {
        RecommendationConfig config = RecommendationTestFixtures.config();
        RecommendationEngine engine = new RecommendationEngine(
                ScorerRegistry.standard(config), config);
        service = new RecommendationService(engine, config, eventBus, chainProvider,
                marketDataProvider, freshnessService,
                Clock.fixed(RecommendationTestFixtures.START, ZoneOffset.UTC));
        eventBus.subscribe(received::add);
        when(chainProvider.snapshotFor(RecommendationTestFixtures.SPOT,
                RecommendationTestFixtures.START))
                .thenReturn(Optional.of(RecommendationTestFixtures.chain()));
        when(marketDataProvider.latestCandle(RecommendationTestFixtures.SPOT,
                RecommendationTestFixtures.INTERVAL))
                .thenReturn(Optional.of(RecommendationTestFixtures.candle()));
        when(marketDataProvider.latestIndicators(RecommendationTestFixtures.SPOT,
                RecommendationTestFixtures.INTERVAL))
                .thenReturn(RecommendationTestFixtures.indicators());
        when(freshnessService.freshness(RecommendationTestFixtures.SPOT))
                .thenReturn(Optional.of(RecommendationTestFixtures.freshness()));
    }

    @Test
    void stagesInputsUntilAllRequiredEvidenceIsPresent() {
        service.onEvent(new RiskAssessmentGeneratedEvent(
                List.of(RecommendationTestFixtures.risk()),
                RecommendationTestFixtures.START));
        service.onEvent(new TradeQualifiedEvent(
                List.of(RecommendationTestFixtures.qualification()),
                RecommendationTestFixtures.START));
        service.onEvent(new StrategyEvaluatedEvent(
                List.of(RecommendationTestFixtures.strategy()),
                RecommendationTestFixtures.START));

        assertThat(received).isEmpty();

        service.onEvent(new MarketAssessmentGeneratedEvent(
                RecommendationTestFixtures.bullishMarket(),
                RecommendationTestFixtures.START));
        assertThat(received).isEmpty();

        service.onEvent(new OptionsAssessmentGeneratedEvent(
                RecommendationTestFixtures.bullishOptions(),
                RecommendationTestFixtures.START));

        assertThat(received).hasSize(1);
        assertThat(received.getFirst()).isInstanceOf(RecommendationGeneratedEvent.class);
        RecommendationGeneratedEvent generated =
                (RecommendationGeneratedEvent) received.getFirst();
        assertThat(generated.recommendation().type())
                .isEqualTo(RecommendationType.BUY_CALL);
    }

    @Test
    void doesNotRepublishIdenticalInputsAndExpiresOnSessionEnd() {
        publishAll();

        service.onEvent(new RiskAssessmentGeneratedEvent(
                List.of(RecommendationTestFixtures.risk()),
                RecommendationTestFixtures.START));

        assertThat(received).hasSize(1);
        assertThat(received.getFirst()).isInstanceOf(RecommendationGeneratedEvent.class);

        service.expire(RecommendationTestFixtures.SPOT,
                RecommendationTestFixtures.INTERVAL, "session closed");

        assertThat(received).hasSize(2);
        assertThat(received.get(1)).isInstanceOf(RecommendationExpiredEvent.class);
        assertThat(service.publishedCount()).isZero();
    }

    @Test
    void publishesAnUpdateWhenTheRecommendationChanges() {
        publishAll();
        RecommendationKey key = new RecommendationKey(
                RecommendationTestFixtures.SPOT, RecommendationTestFixtures.INTERVAL,
                RecommendationTestFixtures.START);
        assertThat(service.current(key)).isPresent();

        service.onEvent(new OptionsAssessmentGeneratedEvent(
                RecommendationTestFixtures.neutralOptions(),
                RecommendationTestFixtures.START));

        assertThat(received).hasSize(2);
        assertThat(received.get(1)).isInstanceOf(RecommendationUpdatedEvent.class);
    }

    @Test
    void skipsStaleCandidatesWithoutFailingTheEventCall() {
        when(freshnessService.freshness(RecommendationTestFixtures.SPOT))
                .thenReturn(Optional.of(RecommendationTestFixtures.staleFreshness()));

        publishAll();

        assertThat(received).isEmpty();
        assertThat(service.publishedCount()).isZero();
    }

    @Test
    void exposesTheLatestRecommendationAndReusableContext() {
        publishAll();

        assertThat(service.latest(RecommendationTestFixtures.SPOT,
                RecommendationTestFixtures.INTERVAL)).isPresent();
        assertThat(service.latestContext(RecommendationTestFixtures.SPOT,
                RecommendationTestFixtures.INTERVAL)).isPresent();
    }

    @Test
    void resolvesARecommendationOnlyByItsExactTimestamp() {
        publishAll();

        assertThat(service.at(RecommendationTestFixtures.SPOT,
                RecommendationTestFixtures.INTERVAL, RecommendationTestFixtures.START))
                .isPresent();
        assertThat(service.contextAt(RecommendationTestFixtures.SPOT,
                RecommendationTestFixtures.INTERVAL, RecommendationTestFixtures.START))
                .isPresent();
        assertThat(service.at(RecommendationTestFixtures.SPOT,
                RecommendationTestFixtures.INTERVAL,
                RecommendationTestFixtures.START.plusSeconds(300))).isEmpty();
        assertThat(service.contextAt(RecommendationTestFixtures.SPOT,
                RecommendationTestFixtures.INTERVAL,
                RecommendationTestFixtures.START.plusSeconds(300))).isEmpty();
    }

    private void publishAll() {
        service.onEvent(new MarketAssessmentGeneratedEvent(
                RecommendationTestFixtures.bullishMarket(),
                RecommendationTestFixtures.START));
        service.onEvent(new OptionsAssessmentGeneratedEvent(
                RecommendationTestFixtures.bullishOptions(),
                RecommendationTestFixtures.START));
        service.onEvent(new StrategyEvaluatedEvent(
                List.of(RecommendationTestFixtures.strategy()),
                RecommendationTestFixtures.START));
        service.onEvent(new TradeQualifiedEvent(
                List.of(RecommendationTestFixtures.qualification()),
                RecommendationTestFixtures.START));
        service.onEvent(new RiskAssessmentGeneratedEvent(
                List.of(RecommendationTestFixtures.risk()),
                RecommendationTestFixtures.START));
    }
}
