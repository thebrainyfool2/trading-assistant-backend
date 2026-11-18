package com.tradingassistant.execution;

import com.tradingassistant.execution.broker.BrokerAdapterRegistry;
import com.tradingassistant.execution.config.ExecutionConfig;
import com.tradingassistant.execution.config.ExecutionMode;
import com.tradingassistant.execution.engine.ExecutionEngine;
import com.tradingassistant.execution.order.OrderStore;
import com.tradingassistant.execution.portfolio.PortfolioService;
import com.tradingassistant.execution.registry.ExecutionRuleRegistry;
import com.tradingassistant.execution.service.ExecutionMarketDataProvider;
import com.tradingassistant.execution.service.ExecutionService;
import com.tradingassistant.pipeline.event.MarketEventBus;
import com.tradingassistant.strategy.StrategyConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ExecutionConfiguration.class,
                    StrategyConfiguration.class)
            .withBean(Clock.class,
                    () -> Clock.fixed(Instant.parse("2026-11-18T03:45:00Z"), ZoneOffset.UTC))
            .withBean(MarketEventBus.class, MarketEventBus::new);

    @Test
    void disabledBrokerKeepsExecutionAbsent() {
        runner.withPropertyValues("app.broker.angel-one.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ExecutionEngine.class);
                    assertThat(context).doesNotHaveBean(ExecutionService.class);
                    assertThat(context).doesNotHaveBean(PortfolioService.class);
                });
    }

    @Test
    void enabledBrokerWiresTheExecutionLayer() {
        runner.withPropertyValues("app.broker.angel-one.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ExecutionConfig.class);
                    assertThat(context).hasSingleBean(ExecutionRuleRegistry.class);
                    assertThat(context).hasSingleBean(BrokerAdapterRegistry.class);
                    assertThat(context).hasSingleBean(PortfolioService.class);
                    assertThat(context).hasSingleBean(OrderStore.class);
                    assertThat(context).hasSingleBean(ExecutionEngine.class);
                    assertThat(context).hasSingleBean(ExecutionService.class);
                    assertThat(context).hasSingleBean(ExecutionMarketDataProvider.class);
                    assertThat(context.getBean(ExecutionRuleRegistry.class).size())
                            .isEqualTo(13);
                    assertThat(context.getBean(BrokerAdapterRegistry.class).snapshot())
                            .extracting(adapter -> adapter.brokerName())
                            .containsExactly("paper");
                });
    }

    @Test
    void thePolicyDefaultsToEnabledPaperTrading() {
        runner.withPropertyValues("app.broker.angel-one.enabled=true")
                .run(context -> {
                    ExecutionConfig config = context.getBean(ExecutionConfig.class);
                    assertThat(config.enabled()).isTrue();
                    assertThat(config.mode()).isEqualTo(ExecutionMode.PAPER);
                    assertThat(config.brokerName()).isEqualTo("paper");
                });
    }

    @Test
    void propertiesOverrideEnabledModeAndBroker() {
        runner.withPropertyValues("app.broker.angel-one.enabled=true",
                        "app.execution.enabled=false",
                        "app.execution.mode=live",
                        "app.execution.broker-name=paper")
                .run(context -> {
                    ExecutionConfig config = context.getBean(ExecutionConfig.class);
                    assertThat(config.enabled()).isFalse();
                    assertThat(config.mode()).isEqualTo(ExecutionMode.LIVE);
                });
    }

    @Test
    void theSubscriptionIsRemovedOnShutdown() {
        MarketEventBus bus = new MarketEventBus();
        new ApplicationContextRunner()
                .withUserConfiguration(ExecutionConfiguration.class,
                        StrategyConfiguration.class)
                .withBean(Clock.class,
                        () -> Clock.fixed(Instant.parse("2026-11-18T03:45:00Z"),
                                ZoneOffset.UTC))
                .withBean(MarketEventBus.class, () -> bus)
                .withPropertyValues("app.broker.angel-one.enabled=true")
                .run(context -> assertThat(bus.subscriberCount())
                        .isGreaterThanOrEqualTo(1));

        assertThat(bus.subscriberCount()).isZero();
    }
}
