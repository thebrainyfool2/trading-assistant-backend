package com.tradingassistant.platform.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the platform's maintenance jobs.
 *
 * <p>Scheduling covers token cleanup, portfolio snapshot capture, notification
 * retention and read-model trimming only. No trading action is ever scheduled.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "app.platform.scheduling", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SchedulingConfiguration {
}
