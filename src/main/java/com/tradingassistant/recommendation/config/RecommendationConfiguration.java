package com.tradingassistant.recommendation.config;

import com.tradingassistant.options.service.OptionChainProvider;
import com.tradingassistant.pipeline.event.MarketEventBus;
import com.tradingassistant.platform.readmodel.MarketDataFreshnessService;
import com.tradingassistant.recommendation.engine.RecommendationEngine;
import com.tradingassistant.recommendation.event.RecommendationEventBus;
import com.tradingassistant.recommendation.registry.ScorerRegistry;
import com.tradingassistant.recommendation.service.RecommendationService;
import com.tradingassistant.strategy.service.StrategyMarketDataProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wires the deterministic recommendation engine into the live pipeline.
 *
 * <p>The recommendation package depends on existing engine outputs. No existing
 * package imports recommendation, and no order, execution or broker adapter is
 * reachable from here.</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.broker.angel-one", name = "enabled",
        havingValue = "true")
public class RecommendationConfiguration {

    @Bean
    public RecommendationConfig recommendationConfig(RecommendationProperties properties) {
        return properties.toConfig();
    }

    @Bean
    public RecommendationEventBus recommendationEventBus() {
        return new RecommendationEventBus();
    }

    @Bean
    public ScorerRegistry recommendationScorerRegistry(RecommendationConfig config) {
        return ScorerRegistry.standard(config);
    }

    @Bean
    public RecommendationEngine recommendationEngine(ScorerRegistry registry,
                                                     RecommendationConfig config) {
        return new RecommendationEngine(registry, config);
    }

    @Bean
    public RecommendationService recommendationService(
            RecommendationEngine engine,
            RecommendationConfig config,
            RecommendationEventBus eventBus,
            OptionChainProvider chainProvider,
            StrategyMarketDataProvider marketDataProvider,
            MarketDataFreshnessService freshnessService,
            Clock clock) {
        return new RecommendationService(engine, config, eventBus, chainProvider,
                marketDataProvider, freshnessService, clock);
    }

    /**
     * Subscribes the service to the pipeline event bus. The bean's inferred
     * destroy method unsubscribes it on shutdown.
     */
    @Bean
    public MarketEventBus.Subscription recommendationSubscription(
            MarketEventBus bus,
            RecommendationService service) {
        return bus.subscribe(service);
    }
}
