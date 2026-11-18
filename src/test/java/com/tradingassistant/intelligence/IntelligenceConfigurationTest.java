package com.tradingassistant.intelligence;

import com.tradingassistant.intelligence.engine.MarketIntelligenceEngine;
import com.tradingassistant.intelligence.service.MarketIntelligenceService;
import com.tradingassistant.pipeline.calendar.MarketCalendarRegistry;
import com.tradingassistant.pipeline.event.MarketEventBus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class IntelligenceConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(IntelligenceConfiguration.class)
            .withBean(Clock.class, () -> Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC))
            .withBean(MarketCalendarRegistry.class, MarketCalendarRegistry::defaults)
            .withBean(MarketEventBus.class, MarketEventBus::new);

    @Test
    void disabledBrokerKeepsIntelligenceAbsent() {
        runner.withPropertyValues("app.broker.angel-one.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MarketIntelligenceEngine.class);
                    assertThat(context).doesNotHaveBean(MarketIntelligenceService.class);
                });
    }

    @Test
    void enabledBrokerWiresTheEngineAndSubscription() {
        runner.withPropertyValues("app.broker.angel-one.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(com.tradingassistant.intelligence.config.MarketIntelligenceConfig.class);
                    assertThat(context).hasSingleBean(MarketIntelligenceEngine.class);
                    assertThat(context).hasSingleBean(MarketIntelligenceService.class);
                    assertThat(context.getBean(MarketEventBus.class).subscriberCount()).isEqualTo(1);
                });
    }

    @Test
    void theSubscriptionIsRemovedOnShutdown() {
        MarketEventBus bus = new MarketEventBus();
        new ApplicationContextRunner()
                .withUserConfiguration(IntelligenceConfiguration.class)
                .withBean(Clock.class, () -> Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC))
                .withBean(MarketCalendarRegistry.class, MarketCalendarRegistry::defaults)
                .withBean(MarketEventBus.class, () -> bus)
                .withPropertyValues("app.broker.angel-one.enabled=true")
                .run(context -> assertThat(context.getBean(MarketEventBus.class).subscriberCount()).isEqualTo(1));

        assertThat(bus.subscriberCount()).isZero();
    }
}
