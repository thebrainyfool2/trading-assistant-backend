package com.tradingassistant.strategy;

import com.tradingassistant.pipeline.event.MarketEventBus;
import com.tradingassistant.pipeline.event.MarketEventPublisher;
import com.tradingassistant.strategy.config.StrategyEngineConfig;
import com.tradingassistant.strategy.engine.StrategyEvaluationEngine;
import com.tradingassistant.strategy.registry.BuiltInStrategies;
import com.tradingassistant.strategy.registry.StrategyRegistry;
import com.tradingassistant.strategy.service.InMemoryStrategyMarketDataProvider;
import com.tradingassistant.strategy.service.StrategyEvaluationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.concurrent.Executor;

/**
 * Wires the strategy evaluation engine into the running pipeline.
 *
 * <p>Like the other evaluation layers, the strategy service is registered only
 * when the broker is enabled. The engine receives a direct executor by default:
 * parallel evaluation is available when a caller supplies an executor, but the
 * application never creates or owns a thread pool. The subscription is a bean, so
 * Spring unsubscribes it on shutdown.</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.broker.angel-one", name = "enabled", havingValue = "true")
public class StrategyConfiguration {

    @Bean
    public StrategyEngineConfig strategyEngineConfig() {
        return StrategyEngineConfig.DEFAULT;
    }

    @Bean
    public StrategyRegistry strategyRegistry(StrategyEngineConfig config) {
        return new StrategyRegistry(config, BuiltInStrategies.all());
    }

    @Bean
    public Executor strategyExecutor() {
        return Runnable::run;
    }

    @Bean
    public StrategyEvaluationEngine strategyEvaluationEngine(StrategyEngineConfig config,
                                                             Executor strategyExecutor) {
        return new StrategyEvaluationEngine(config, strategyExecutor);
    }

    @Bean
    public InMemoryStrategyMarketDataProvider strategyMarketDataProvider() {
        return new InMemoryStrategyMarketDataProvider();
    }

    @Bean
    public StrategyEvaluationService strategyEvaluationService(
            StrategyEvaluationEngine engine,
            StrategyRegistry registry,
            StrategyEngineConfig config,
            InMemoryStrategyMarketDataProvider provider,
            MarketEventPublisher publisher,
            Clock clock) {
        return new StrategyEvaluationService(engine, registry, config, provider, publisher,
                clock);
    }

    /**
     * Subscribes the service to the pipeline event bus. The bean's inferred
     * destroy method unsubscribes it on shutdown.
     */
    @Bean
    public MarketEventBus.Subscription strategyEvaluationSubscription(
            MarketEventBus bus,
            StrategyEvaluationService service) {
        return bus.subscribe(service);
    }
}
