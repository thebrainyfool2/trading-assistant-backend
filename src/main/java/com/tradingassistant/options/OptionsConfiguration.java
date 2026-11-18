package com.tradingassistant.options;

import com.tradingassistant.options.chain.OptionChainBuilder;
import com.tradingassistant.options.config.OptionsAnalyticsConfig;
import com.tradingassistant.options.engine.OptionsAnalyticsEngine;
import com.tradingassistant.options.service.InMemoryOptionChainProvider;
import com.tradingassistant.options.service.OptionsAnalyticsService;
import com.tradingassistant.pipeline.event.MarketEventBus;
import com.tradingassistant.pipeline.event.MarketEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wires the option analytics engine into the running pipeline.
 *
 * <p>Like the market services, pipeline and intelligence, the options layer is
 * registered only when the broker is enabled: with no feed there are no chains
 * to describe. The subscription is a bean, so Spring unsubscribes it on shutdown.</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.broker.angel-one", name = "enabled", havingValue = "true")
public class OptionsConfiguration {

    @Bean
    public OptionsAnalyticsConfig optionsAnalyticsConfig() {
        return OptionsAnalyticsConfig.DEFAULT;
    }

    @Bean
    public OptionChainBuilder optionChainBuilder(OptionsAnalyticsConfig config) {
        return new OptionChainBuilder(config);
    }

    @Bean
    public InMemoryOptionChainProvider optionChainProvider(OptionChainBuilder builder) {
        return new InMemoryOptionChainProvider(builder);
    }

    @Bean
    public OptionsAnalyticsEngine optionsAnalyticsEngine(OptionsAnalyticsConfig config) {
        return OptionsAnalyticsEngine.standard(config);
    }

    @Bean
    public OptionsAnalyticsService optionsAnalyticsService(
            OptionsAnalyticsEngine engine,
            OptionsAnalyticsConfig config,
            InMemoryOptionChainProvider provider,
            MarketEventPublisher publisher,
            Clock clock) {
        return new OptionsAnalyticsService(engine, config, provider, publisher, clock);
    }

    /**
     * Subscribes the service to the pipeline event bus. The bean's inferred
     * destroy method unsubscribes it on shutdown.
     */
    @Bean
    public MarketEventBus.Subscription optionsAssessmentSubscription(
            MarketEventBus bus,
            OptionsAnalyticsService service) {
        return bus.subscribe(service);
    }
}
