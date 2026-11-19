package com.tradingassistant.platform.config;

import com.mongodb.MongoClientSettings;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Bounds MongoDB client waits.
 *
 * <p>A database outage must surface as a prompt, documented unavailable response
 * instead of tying up request threads for the driver's default timeout.</p>
 */
@Configuration(proxyBeanMethods = false)
public class MongoClientConfiguration {

    @Bean
    public MongoClientSettingsBuilderCustomizer mongoTimeoutCustomizer() {
        return (MongoClientSettings.Builder builder) -> builder
                .applyToClusterSettings(cluster -> cluster
                        .serverSelectionTimeout(5, TimeUnit.SECONDS))
                .applyToSocketSettings(socket -> socket
                        .connectTimeout(5, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS));
    }
}
