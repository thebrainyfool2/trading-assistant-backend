package com.tradingassistant.platform.trade;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TradePlanConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TradePlanConfiguration.class,
                    PropertiesConfiguration.class)
            .withPropertyValues(
                    "app.trade-plan.delta-fallback=0.50",
                    "app.trade-plan.fallback-target-percent=40",
                    "app.trade-plan.fallback-stop-percent=20",
                    "app.trade-plan.premium-refresh-ms=30000",
                    "app.trade-plan.premium-alerts-enabled=true");

    @Test
    void disabledBrokerKeepsThePlanConfigAbsent() {
        runner.withPropertyValues("app.broker.angel-one.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(TradePlanConfig.class));
    }

    @Test
    void enabledBrokerBindsThePlanPolicy() {
        runner.withPropertyValues("app.broker.angel-one.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    TradePlanConfig config = context.getBean(TradePlanConfig.class);
                    assertThat(config.deltaFallback()).isEqualByComparingTo("0.50");
                    assertThat(config.fallbackTargetPercent())
                            .isEqualByComparingTo(new BigDecimal("40"));
                    assertThat(config.premiumRefreshMillis()).isEqualTo(30_000L);
                    assertThat(config.premiumAlertsEnabled()).isTrue();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TradePlanProperties.class)
    static class PropertiesConfiguration {
    }
}
