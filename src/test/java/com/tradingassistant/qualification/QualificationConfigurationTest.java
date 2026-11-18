package com.tradingassistant.qualification;

import com.tradingassistant.pipeline.event.MarketEventBus;
import com.tradingassistant.qualification.config.TradeQualificationConfig;
import com.tradingassistant.qualification.domain.QualificationLedger;
import com.tradingassistant.qualification.engine.TradeQualificationEngine;
import com.tradingassistant.qualification.registry.QualificationRuleRegistry;
import com.tradingassistant.qualification.service.TradeQualificationService;
import com.tradingassistant.strategy.StrategyConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class QualificationConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(QualificationConfiguration.class, StrategyConfiguration.class)
            .withBean(Clock.class,
                    () -> Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC))
            .withBean(MarketEventBus.class, MarketEventBus::new);

    @Test
    void disabledBrokerKeepsQualificationAbsent() {
        runner.withPropertyValues("app.broker.angel-one.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(TradeQualificationEngine.class);
                    assertThat(context).doesNotHaveBean(TradeQualificationService.class);
                    assertThat(context).doesNotHaveBean(QualificationLedger.class);
                });
    }

    @Test
    void enabledBrokerWiresTheEngineWithTheBuiltInRules() {
        runner.withPropertyValues("app.broker.angel-one.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TradeQualificationConfig.class);
                    assertThat(context).hasSingleBean(QualificationLedger.class);
                    assertThat(context).hasSingleBean(TradeQualificationEngine.class);
                    assertThat(context).hasSingleBean(TradeQualificationService.class);
                    assertThat(context.getBean(QualificationRuleRegistry.class).size())
                            .isEqualTo(11);
                    assertThat(context.getBean(MarketEventBus.class).subscriberCount())
                            .isEqualTo(2);
                });
    }

    @Test
    void theSubscriptionIsRemovedOnShutdown() {
        MarketEventBus bus = new MarketEventBus();
        new ApplicationContextRunner()
                .withUserConfiguration(QualificationConfiguration.class,
                        StrategyConfiguration.class)
                .withBean(Clock.class,
                        () -> Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC))
                .withBean(MarketEventBus.class, () -> bus)
                .withPropertyValues("app.broker.angel-one.enabled=true")
                .run(context -> assertThat(context.getBean(MarketEventBus.class).subscriberCount())
                        .isEqualTo(2));

        assertThat(bus.subscriberCount()).isZero();
    }
}
