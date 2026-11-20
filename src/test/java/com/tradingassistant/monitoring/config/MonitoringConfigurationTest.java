package com.tradingassistant.monitoring.config;

import com.tradingassistant.monitoring.InMemoryMonitoringStore;
import com.tradingassistant.monitoring.engine.TradeMonitoringEngine;
import com.tradingassistant.monitoring.event.MonitoringEventBus;
import com.tradingassistant.monitoring.service.MonitoringQueryService;
import com.tradingassistant.monitoring.service.MonitoringService;
import com.tradingassistant.monitoring.service.MonitoringStore;
import com.tradingassistant.pipeline.event.MarketEventBus;
import com.tradingassistant.recommendation.event.RecommendationEventBus;
import com.tradingassistant.recommendation.service.RecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MonitoringConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MonitoringConfiguration.class,
                    PropertiesConfiguration.class)
            .withBean(Clock.class,
                    () -> Clock.fixed(Instant.parse("2026-11-18T03:45:00Z"),
                            ZoneOffset.UTC))
            .withBean(MarketEventBus.class, MarketEventBus::new)
            .withBean(RecommendationEventBus.class, RecommendationEventBus::new)
            .withBean(RecommendationService.class, () -> mock(RecommendationService.class))
            .withBean(MonitoringStore.class, InMemoryMonitoringStore::new);

    @Test
    void disabledBrokerKeepsMonitoringAbsent() {
        runner.withPropertyValues("app.broker.angel-one.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MonitoringService.class);
                    assertThat(context).doesNotHaveBean(TradeMonitoringEngine.class);
                });
    }

    @Test
    void enabledBrokerWiresEngineServiceQueriesAndSubscriptions() {
        runner.withPropertyValues(properties()).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(MonitoringConfig.class);
            assertThat(context).hasSingleBean(MonitoringEventBus.class);
            assertThat(context).hasSingleBean(TradeMonitoringEngine.class);
            assertThat(context).hasSingleBean(MonitoringService.class);
            assertThat(context).hasSingleBean(MonitoringQueryService.class);
            assertThat(context.getBean(MarketEventBus.class).subscriberCount())
                    .isEqualTo(1);
            assertThat(context.getBean(RecommendationEventBus.class).subscriberCount())
                    .isEqualTo(1);
        });
    }

    @Test
    void subscriptionsAreRemovedOnShutdown() {
        MarketEventBus marketBus = new MarketEventBus();
        RecommendationEventBus recommendationBus = new RecommendationEventBus();
        new ApplicationContextRunner()
                .withUserConfiguration(MonitoringConfiguration.class,
                        PropertiesConfiguration.class)
                .withBean(Clock.class,
                        () -> Clock.fixed(Instant.parse("2026-11-18T03:45:00Z"),
                                ZoneOffset.UTC))
                .withBean(MarketEventBus.class, () -> marketBus)
                .withBean(RecommendationEventBus.class, () -> recommendationBus)
                .withBean(RecommendationService.class,
                        () -> mock(RecommendationService.class))
                .withBean(MonitoringStore.class, InMemoryMonitoringStore::new)
                .withPropertyValues(properties())
                .run(context -> assertThat(context.getBean(MonitoringService.class))
                        .isNotNull());

        assertThat(marketBus.subscriberCount()).isZero();
        assertThat(recommendationBus.subscriberCount()).isZero();
    }

    private static String[] properties() {
        return new String[]{
                "app.broker.angel-one.enabled=true",
                "app.monitoring.enabled=true",
                "app.monitoring.warning-confidence=65",
                "app.monitoring.exit-confidence=45",
                "app.monitoring.confidence-deterioration=10",
                "app.monitoring.edge-deterioration=8",
                "app.monitoring.component-deterioration=10",
                "app.monitoring.warning-component-count=1",
                "app.monitoring.at-risk-component-count=3",
                "app.monitoring.strategy-disagreement-score=50",
                "app.monitoring.maximum-stale-duration=2m",
                "app.monitoring.monitoring-interval=EVENT_DRIVEN"
        };
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MonitoringProperties.class)
    static class PropertiesConfiguration {
    }
}