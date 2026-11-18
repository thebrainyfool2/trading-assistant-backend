package com.tradingassistant.strategy;

import com.tradingassistant.pipeline.event.MarketEventBus;
import com.tradingassistant.strategy.config.StrategyEngineConfig;
import com.tradingassistant.strategy.engine.StrategyEvaluationEngine;
import com.tradingassistant.strategy.registry.StrategyRegistry;
import com.tradingassistant.strategy.service.StrategyEvaluationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(StrategyConfiguration.class)
            .withBean(Clock.class,
                    () -> Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC))
            .withBean(MarketEventBus.class, MarketEventBus::new);

    @Test
    void disabledBrokerKeepsStrategiesAbsent() {
        runner.withPropertyValues("app.broker.angel-one.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(StrategyEvaluationEngine.class);
                    assertThat(context).doesNotHaveBean(StrategyEvaluationService.class);
                });
    }

    @Test
    void enabledBrokerWiresTheEngineWithBuiltInStrategies() {
        runner.withPropertyValues("app.broker.angel-one.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(StrategyEngineConfig.class);
                    assertThat(context).hasSingleBean(StrategyRegistry.class);
                    assertThat(context).hasSingleBean(StrategyEvaluationEngine.class);
                    assertThat(context).hasSingleBean(StrategyEvaluationService.class);
                    assertThat(context.getBean(StrategyRegistry.class).size()).isEqualTo(15);
                    assertThat(context.getBean(MarketEventBus.class).subscriberCount())
                            .isEqualTo(1);
                });
    }

    @Test
    void theSubscriptionIsRemovedOnShutdown() {
        MarketEventBus bus = new MarketEventBus();
        new ApplicationContextRunner()
                .withUserConfiguration(StrategyConfiguration.class)
                .withBean(Clock.class,
                        () -> Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC))
                .withBean(MarketEventBus.class, () -> bus)
                .withPropertyValues("app.broker.angel-one.enabled=true")
                .run(context -> assertThat(context.getBean(MarketEventBus.class).subscriberCount())
                        .isEqualTo(1));

        assertThat(bus.subscriberCount()).isZero();
    }
}
