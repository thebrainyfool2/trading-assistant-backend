package com.tradingassistant.intelligence;

import com.tradingassistant.intelligence.config.MarketIntelligenceConfig;
import com.tradingassistant.intelligence.engine.MarketIntelligenceEngine;
import com.tradingassistant.intelligence.service.MarketIntelligenceService;
import com.tradingassistant.pipeline.calendar.MarketCalendarRegistry;
import com.tradingassistant.pipeline.event.MarketEventBus;
import com.tradingassistant.pipeline.event.MarketEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wires the market-intelligence engine into the running pipeline.
 *
 * <p>The subscription is a bean, so Spring closes it on shutdown and the engine
 * stops receiving updates with the bus. Like the market services and the
 * pipeline, intelligence is registered only when the broker is enabled: with no
 * feed there are no candles to describe.</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.broker.angel-one", name = "enabled", havingValue = "true")
public class IntelligenceConfiguration {

    @Bean
    public MarketIntelligenceConfig marketIntelligenceConfig() {
        return MarketIntelligenceConfig.defaults();
    }

    @Bean
    public MarketIntelligenceEngine marketIntelligenceEngine(MarketIntelligenceConfig config) {
        return MarketIntelligenceEngine.standard(config);
    }

    @Bean
    public MarketIntelligenceService marketIntelligenceService(
            MarketIntelligenceEngine engine,
            MarketIntelligenceConfig config,
            MarketCalendarRegistry calendars,
            MarketEventPublisher publisher,
            Clock clock) {
        return new MarketIntelligenceService(engine, config, calendars, publisher, clock);
    }

    /**
     * Subscribes the service to the pipeline event bus. The bean's inferred
     * destroy method unsubscribes it on shutdown.
     */
    @Bean
    public MarketEventBus.Subscription marketIntelligenceSubscription(
            MarketEventBus bus,
            MarketIntelligenceService service) {
        return bus.subscribe(service);
    }
}
