package com.tradingassistant.options;

import com.tradingassistant.options.chain.OptionChainBuilder;
import com.tradingassistant.options.config.OptionsAnalyticsConfig;
import com.tradingassistant.options.engine.OptionsAnalyticsEngine;
import com.tradingassistant.options.service.InMemoryOptionChainProvider;
import com.tradingassistant.options.service.OptionsAnalyticsService;
import com.tradingassistant.pipeline.event.MarketEventBus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class OptionsConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(OptionsConfiguration.class)
            .withBean(Clock.class,
                    () -> Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC))
            .withBean(MarketEventBus.class, MarketEventBus::new);

    @Test
    void disabledBrokerKeepsOptionsAbsent() {
        runner.withPropertyValues("app.broker.angel-one.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(OptionsAnalyticsEngine.class);
                    assertThat(context).doesNotHaveBean(OptionsAnalyticsService.class);
                });
    }

    @Test
    void enabledBrokerWiresTheEngineAndSubscription() {
        runner.withPropertyValues("app.broker.angel-one.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OptionsAnalyticsConfig.class);
                    assertThat(context).hasSingleBean(OptionChainBuilder.class);
                    assertThat(context).hasSingleBean(InMemoryOptionChainProvider.class);
                    assertThat(context).hasSingleBean(OptionsAnalyticsEngine.class);
                    assertThat(context).hasSingleBean(OptionsAnalyticsService.class);
                    assertThat(context.getBean(MarketEventBus.class).subscriberCount())
                            .isEqualTo(1);
                });
    }

    @Test
    void theSubscriptionIsRemovedOnShutdown() {
        MarketEventBus bus = new MarketEventBus();
        new ApplicationContextRunner()
                .withUserConfiguration(OptionsConfiguration.class)
                .withBean(Clock.class,
                        () -> Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC))
                .withBean(MarketEventBus.class, () -> bus)
                .withPropertyValues("app.broker.angel-one.enabled=true")
                .run(context -> assertThat(context.getBean(MarketEventBus.class).subscriberCount())
                        .isEqualTo(1));

        assertThat(bus.subscriberCount()).isZero();
    }
}
