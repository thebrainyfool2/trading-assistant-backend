package com.tradingassistant.platform.trade;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the application-level trade plan next to the broker-backed engines. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.broker.angel-one", name = "enabled",
        havingValue = "true")
public class TradePlanConfiguration {

    @Bean
    public TradePlanConfig tradePlanConfig(TradePlanProperties properties) {
        return properties.toConfig();
    }
}
