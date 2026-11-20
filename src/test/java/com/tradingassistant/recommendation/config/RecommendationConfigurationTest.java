package com.tradingassistant.recommendation.config;

import com.tradingassistant.options.service.OptionChainProvider;
import com.tradingassistant.pipeline.event.MarketEventBus;
import com.tradingassistant.platform.readmodel.MarketDataFreshnessService;
import com.tradingassistant.recommendation.engine.RecommendationEngine;
import com.tradingassistant.recommendation.event.RecommendationEventBus;
import com.tradingassistant.recommendation.registry.ScorerRegistry;
import com.tradingassistant.recommendation.service.RecommendationService;
import com.tradingassistant.strategy.service.StrategyMarketDataProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RecommendationConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RecommendationConfiguration.class,
                    PropertiesConfiguration.class)
            .withBean(Clock.class,
                    () -> Clock.fixed(Instant.parse("2026-11-18T03:45:00Z"),
                            ZoneOffset.UTC))
            .withBean(MarketEventBus.class, MarketEventBus::new)
            .withBean(OptionChainProvider.class, () -> mock(OptionChainProvider.class))
            .withBean(StrategyMarketDataProvider.class,
                    () -> mock(StrategyMarketDataProvider.class))
            .withBean(MarketDataFreshnessService.class,
                    () -> mock(MarketDataFreshnessService.class));

    @Test
    void disabledBrokerKeepsRecommendationAbsent() {
        runner.withPropertyValues("app.broker.angel-one.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RecommendationEngine.class);
                    assertThat(context).doesNotHaveBean(RecommendationService.class);
                });
    }

    @Test
    void enabledBrokerWiresTheDeterministicEngine() {
        runner.withPropertyValues(properties()).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RecommendationConfig.class);
            assertThat(context).hasSingleBean(RecommendationEventBus.class);
            assertThat(context).hasSingleBean(ScorerRegistry.class);
            assertThat(context).hasSingleBean(RecommendationEngine.class);
            assertThat(context).hasSingleBean(RecommendationService.class);
            assertThat(context.getBean(ScorerRegistry.class).size())
                    .isEqualTo(com.tradingassistant.recommendation.domain.ScorerId
                            .values().length);
            assertThat(context.getBean(MarketEventBus.class).subscriberCount())
                    .isEqualTo(1);
            assertThat(context.getBean(RecommendationConfig.class)
                    .allowedExpiryKinds()).hasSize(2);
        });
    }

    @Test
    void subscriptionIsRemovedOnShutdown() {
        MarketEventBus bus = new MarketEventBus();
        new ApplicationContextRunner()
                .withUserConfiguration(RecommendationConfiguration.class,
                        PropertiesConfiguration.class)
                .withBean(Clock.class,
                        () -> Clock.fixed(Instant.parse("2026-11-18T03:45:00Z"),
                                ZoneOffset.UTC))
                .withBean(MarketEventBus.class, () -> bus)
                .withBean(OptionChainProvider.class,
                        () -> mock(OptionChainProvider.class))
                .withBean(StrategyMarketDataProvider.class,
                        () -> mock(StrategyMarketDataProvider.class))
                .withBean(MarketDataFreshnessService.class,
                        () -> mock(MarketDataFreshnessService.class))
                .withPropertyValues(properties())
                .run(context -> assertThat(context.getBean(MarketEventBus.class)
                        .subscriberCount()).isEqualTo(1));

        assertThat(bus.subscriberCount()).isZero();
    }

    private static String[] properties() {
        List<String> values = new ArrayList<>(List.of(
                "app.broker.angel-one.enabled=true",
                "app.recommendation.enabled=true",
                "app.recommendation.minimum-confidence=55",
                "app.recommendation.minimum-directional-edge=5",
                "app.recommendation.pcr-bullish-threshold=1.20",
                "app.recommendation.pcr-bearish-threshold=0.80",
                "app.recommendation.high-iv-rank=80",
                "app.recommendation.low-iv-rank=20",
                "app.recommendation.allowed-expiry-kinds=WEEKLY,MONTHLY",
                "app.recommendation.expiry-selection=NEAREST",
                "app.recommendation.strike-mode=ATM",
                "app.recommendation.strike-offset-steps=0",
                "app.recommendation.maximum-strike-steps=5"));
        for (com.tradingassistant.recommendation.domain.ScorerId id
                : com.tradingassistant.recommendation.domain.ScorerId.values()) {
            values.add("app.recommendation.weights." + id.id() + "=5");
        }
        return values.toArray(String[]::new);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RecommendationProperties.class)
    static class PropertiesConfiguration {
    }
}
