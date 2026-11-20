package com.tradingassistant.monitoring.config;

import com.tradingassistant.monitoring.engine.TradeMonitoringEngine;
import com.tradingassistant.monitoring.event.MonitoringEventBus;
import com.tradingassistant.monitoring.service.MonitoringQueryService;
import com.tradingassistant.monitoring.service.MonitoringService;
import com.tradingassistant.monitoring.service.MonitoringStore;
import com.tradingassistant.pipeline.event.MarketEventBus;
import com.tradingassistant.recommendation.event.RecommendationEventBus;
import com.tradingassistant.recommendation.service.RecommendationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wires continuous monitoring above the existing engines. It subscribes to
 * completed candles and recommendation updates only; no polling loop, broker
 * adapter or execution component is reachable from this package.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.broker.angel-one", name = "enabled",
        havingValue = "true")
public class MonitoringConfiguration {

    @Bean
    public MonitoringConfig monitoringConfig(MonitoringProperties properties) {
        return properties.toConfig();
    }

    @Bean
    public MonitoringEventBus monitoringEventBus() {
        return new MonitoringEventBus();
    }

    @Bean
    public TradeMonitoringEngine tradeMonitoringEngine(MonitoringConfig config) {
        return new TradeMonitoringEngine(config);
    }

    @Bean
    public MonitoringService monitoringService(RecommendationService recommendations,
                                               TradeMonitoringEngine engine,
                                               MonitoringStore store,
                                               MonitoringEventBus eventBus,
                                               MonitoringConfig config,
                                               Clock clock) {
        return new MonitoringService(recommendations, engine, store, eventBus, config,
                clock);
    }

    @Bean
    public MonitoringQueryService monitoringQueryService(MonitoringStore store) {
        return new MonitoringQueryService(store);
    }

    @Bean(destroyMethod = "close")
    public MarketEventBus.Subscription monitoringMarketSubscription(
            MarketEventBus bus, MonitoringService service) {
        return bus.subscribe(service);
    }

    @Bean(destroyMethod = "close")
    public AutoCloseable monitoringRecommendationSubscription(
            RecommendationEventBus bus, MonitoringService service) {
        return bus.subscribe(service::onRecommendationEvent);
    }
}