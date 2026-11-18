package com.tradingassistant.risk;

import com.tradingassistant.pipeline.event.MarketEventBus;
import com.tradingassistant.qualification.QualificationConfiguration;
import com.tradingassistant.risk.config.RiskEngineConfig;
import com.tradingassistant.risk.engine.RiskEngine;
import com.tradingassistant.risk.registry.RiskRuleRegistry;
import com.tradingassistant.risk.service.RiskAssessmentService;
import com.tradingassistant.risk.service.RiskMarketDataProvider;
import com.tradingassistant.strategy.StrategyConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RiskConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RiskConfiguration.class, QualificationConfiguration.class,
                    StrategyConfiguration.class)
            .withBean(Clock.class,
                    () -> Clock.fixed(Instant.parse("2026-11-18T03:45:00Z"), ZoneOffset.UTC))
            .withBean(MarketEventBus.class, MarketEventBus::new);

    @Test
    void disabledBrokerKeepsRiskAbsent() {
        runner.withPropertyValues("app.broker.angel-one.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RiskEngine.class);
                    assertThat(context).doesNotHaveBean(RiskAssessmentService.class);
                    assertThat(context).doesNotHaveBean(RiskMarketDataProvider.class);
                });
    }

    @Test
    void enabledBrokerWiresTheEngineWithTheBuiltInRules() {
        runner.withPropertyValues("app.broker.angel-one.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RiskEngineConfig.class);
                    assertThat(context).hasSingleBean(RiskRuleRegistry.class);
                    assertThat(context).hasSingleBean(RiskEngine.class);
                    assertThat(context).hasSingleBean(RiskAssessmentService.class);
                    assertThat(context).hasSingleBean(RiskMarketDataProvider.class);
                    assertThat(context.getBean(RiskRuleRegistry.class).size()).isEqualTo(15);
                    assertThat(context.getBean(MarketEventBus.class).subscriberCount())
                            .isEqualTo(3);
                });
    }

    @Test
    void theSubscriptionIsRemovedOnShutdown() {
        MarketEventBus bus = new MarketEventBus();
        new ApplicationContextRunner()
                .withUserConfiguration(RiskConfiguration.class,
                        QualificationConfiguration.class, StrategyConfiguration.class)
                .withBean(Clock.class,
                        () -> Clock.fixed(Instant.parse("2026-11-18T03:45:00Z"),
                                ZoneOffset.UTC))
                .withBean(MarketEventBus.class, () -> bus)
                .withPropertyValues("app.broker.angel-one.enabled=true")
                .run(context -> assertThat(context.getBean(MarketEventBus.class)
                        .subscriberCount()).isEqualTo(3));

        assertThat(bus.subscriberCount()).isZero();
    }
}
