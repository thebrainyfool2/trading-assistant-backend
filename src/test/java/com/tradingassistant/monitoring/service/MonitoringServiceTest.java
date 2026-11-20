package com.tradingassistant.monitoring.service;

import com.tradingassistant.monitoring.InMemoryMonitoringStore;
import com.tradingassistant.monitoring.MonitoringTestFixtures;
import com.tradingassistant.monitoring.domain.ManualTradeStatus;
import com.tradingassistant.monitoring.domain.MonitoringAlertType;
import com.tradingassistant.monitoring.domain.MonitoringState;
import com.tradingassistant.monitoring.engine.TradeMonitoringEngine;
import com.tradingassistant.monitoring.event.ExitRecommendedEvent;
import com.tradingassistant.monitoring.event.MonitoringEvent;
import com.tradingassistant.monitoring.event.MonitoringEventBus;
import com.tradingassistant.monitoring.event.MonitoringStartedEvent;
import com.tradingassistant.monitoring.event.MonitoringStateChangedEvent;
import com.tradingassistant.monitoring.event.MonitoringStoppedEvent;
import com.tradingassistant.monitoring.exception.MonitoringValidationException;
import com.tradingassistant.pipeline.event.CandleCompletedEvent;
import com.tradingassistant.recommendation.RecommendationTestFixtures;
import com.tradingassistant.recommendation.domain.RecommendationContext;
import com.tradingassistant.recommendation.event.RecommendationUpdatedEvent;
import com.tradingassistant.recommendation.service.RecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MonitoringServiceTest {

    private final RecommendationService recommendations = mock(RecommendationService.class);
    private final InMemoryMonitoringStore store = new InMemoryMonitoringStore();
    private final MonitoringEventBus eventBus = new MonitoringEventBus();
    private final List<MonitoringEvent> received = new ArrayList<>();
    private final RecommendationContext source = RecommendationTestFixtures.bullishContext();
    private MonitoringService service;

    @BeforeEach
    void setUp() {
        service = new MonitoringService(recommendations,
                new TradeMonitoringEngine(MonitoringTestFixtures.config()), store, eventBus,
                MonitoringTestFixtures.config(),
                Clock.fixed(MonitoringTestFixtures.START, ZoneOffset.UTC));
        eventBus.subscribe(received::add);
        when(recommendations.latest(MonitoringTestFixtures.SPOT,
                MonitoringTestFixtures.INTERVAL))
                .thenReturn(Optional.of(MonitoringTestFixtures.defaultCall()));
        when(recommendations.latestContext(MonitoringTestFixtures.SPOT,
                MonitoringTestFixtures.INTERVAL)).thenReturn(Optional.of(source));
    }

    @Test
    void registerStartsMonitoringAndPersistsTheFirstResult() {
        service.register(MonitoringTestFixtures.callTrade());

        assertThat(store.activeTrades()).hasSize(1);
        assertThat(store.latestResult(MonitoringTestFixtures.TRADE_ID)).get()
                .extracting(result -> result.monitoringState())
                .isEqualTo(MonitoringState.HEALTHY);
        assertThat(store.history()).hasSize(1);
        assertThat(received).filteredOn(MonitoringStartedEvent.class::isInstance)
                .hasSize(1);
    }

    @Test
    void duplicateRegistrationAndMissingRecommendationAreRejected() {
        service.register(MonitoringTestFixtures.callTrade());

        assertThatThrownBy(() -> service.register(MonitoringTestFixtures.callTrade()))
                .isInstanceOf(MonitoringValidationException.class)
                .hasMessageContaining("already exists");

        MonitoringService noRecommendations = new MonitoringService(recommendations,
                new TradeMonitoringEngine(MonitoringTestFixtures.config()),
                new InMemoryMonitoringStore(), eventBus, MonitoringTestFixtures.config(),
                Clock.fixed(MonitoringTestFixtures.START, ZoneOffset.UTC));
        when(recommendations.latest(MonitoringTestFixtures.SPOT,
                MonitoringTestFixtures.INTERVAL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noRecommendations.register(
                MonitoringTestFixtures.callTrade()))
                .isInstanceOf(MonitoringValidationException.class)
                .hasMessageContaining("no recommendation");
    }

    @Test
    void duplicateScansDoNotDuplicateHistoryOrEvents() {
        service.register(MonitoringTestFixtures.callTrade());

        service.rescan(MonitoringTestFixtures.SPOT, MonitoringTestFixtures.INTERVAL);
        service.rescan(MonitoringTestFixtures.SPOT, MonitoringTestFixtures.INTERVAL);

        assertThat(store.history()).hasSize(1);
        assertThat(received).filteredOn(MonitoringStartedEvent.class::isInstance)
                .hasSize(1);
    }

    @Test
    void completedCandleTriggersARescanOfActiveTrades() {
        service.register(MonitoringTestFixtures.callTrade());
        when(recommendations.latest(MonitoringTestFixtures.SPOT,
                MonitoringTestFixtures.INTERVAL))
                .thenReturn(Optional.of(MonitoringTestFixtures.weakCall()));

        service.onEvent(new CandleCompletedEvent(
                RecommendationTestFixtures.candle(), MonitoringTestFixtures.START));

        assertThat(store.latestResult(MonitoringTestFixtures.TRADE_ID)).get()
                .extracting(result -> result.monitoringState())
                .isEqualTo(MonitoringState.AT_RISK);
    }

    @Test
    void recommendationUpdateCanChangeStateAndWriteHistoryAlertsAndEvents() {
        service.register(MonitoringTestFixtures.callTrade());
        when(recommendations.latest(MonitoringTestFixtures.SPOT,
                MonitoringTestFixtures.INTERVAL))
                .thenReturn(Optional.of(MonitoringTestFixtures.weakCall()));

        service.onRecommendationEvent(new RecommendationUpdatedEvent(
                MonitoringTestFixtures.weakCall(), MonitoringTestFixtures.START));

        assertThat(store.latestResult(MonitoringTestFixtures.TRADE_ID)).get()
                .extracting(result -> result.monitoringState())
                .isEqualTo(MonitoringState.AT_RISK);
        assertThat(store.history()).hasSize(2);
        assertThat(received).filteredOn(MonitoringStateChangedEvent.class::isInstance)
                .hasSize(1);
        assertThat(received).filteredOn(ExitRecommendedEvent.class::isInstance)
                .hasSize(1);
        assertThat(store.alerts()).extracting(alert -> alert.type())
                .contains(MonitoringAlertType.STATE_CHANGED);
    }

    @Test
    void staleContextProducesAnAlertWithoutOverwritingTheLastResult() {
        RecommendationContext stale = new RecommendationContext(
                RecommendationTestFixtures.bullishMarket(),
                Optional.of(RecommendationTestFixtures.bullishOptions()),
                Optional.of(RecommendationTestFixtures.chain()),
                RecommendationTestFixtures.indicators(),
                Optional.of(RecommendationTestFixtures.candle()),
                List.of(RecommendationTestFixtures.strategy()),
                List.of(RecommendationTestFixtures.qualification()),
                List.of(RecommendationTestFixtures.risk()),
                MonitoringTestFixtures.staleFreshness());
        when(recommendations.latestContext(MonitoringTestFixtures.SPOT,
                MonitoringTestFixtures.INTERVAL)).thenReturn(Optional.of(stale));

        service.register(MonitoringTestFixtures.callTrade());

        assertThat(store.latestResult(MonitoringTestFixtures.TRADE_ID)).isEmpty();
        assertThat(store.alerts()).extracting(alert -> alert.type())
                .contains(MonitoringAlertType.STALE_DATA);
        assertThat(received).filteredOn(MonitoringStartedEvent.class::isInstance)
                .isEmpty();
    }

    @Test
    void closingATradeStopsMonitoringWithoutExecution() {
        service.register(MonitoringTestFixtures.callTrade());

        service.close(MonitoringTestFixtures.TRADE_ID, "owner exited manually");

        assertThat(store.trade(MonitoringTestFixtures.TRADE_ID)).get()
                .extracting(trade -> trade.status())
                .isEqualTo(ManualTradeStatus.CLOSED);
        assertThat(store.activeTrades()).isEmpty();
        assertThat(received).filteredOn(MonitoringStoppedEvent.class::isInstance)
                .hasSize(1);
        assertThat(store.alerts()).extracting(alert -> alert.type())
                .contains(MonitoringAlertType.MONITORING_STOPPED);
    }
}