package com.tradingassistant.execution;

import com.tradingassistant.execution.broker.BrokerAdapterRegistry;
import com.tradingassistant.execution.broker.PaperBrokerAdapter;
import com.tradingassistant.execution.config.ExecutionConfig;
import com.tradingassistant.execution.config.ExecutionMode;
import com.tradingassistant.execution.engine.ExecutionEngine;
import com.tradingassistant.execution.event.ExecutionEventSink;
import com.tradingassistant.execution.order.OrderStore;
import com.tradingassistant.execution.portfolio.PortfolioService;
import com.tradingassistant.execution.registry.BuiltInExecutionRules;
import com.tradingassistant.execution.registry.ExecutionRuleRegistry;
import com.tradingassistant.execution.service.ExecutionMarketDataProvider;
import com.tradingassistant.execution.service.ExecutionService;
import com.tradingassistant.pipeline.event.MarketEventBus;
import com.tradingassistant.pipeline.event.MarketEventPublisher;
import com.tradingassistant.strategy.service.StrategyMarketDataProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wires the execution layer into the running pipeline.
 *
 * <p>Execution is registered only when the broker is enabled, exactly like the
 * evaluation stages before it. The policy is immutable and built once; the paper
 * adapter is the only adapter the application ships, so live mode is refused until
 * a live adapter exists.</p>
 *
 * <p>Execution events are published through the same bus the pipeline uses. The
 * service subscribes to that bus, so an execution result is observable by every
 * other subscriber without the engine knowing who listens.</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.broker.angel-one", name = "enabled",
        havingValue = "true")
public class ExecutionConfiguration {

    @Bean
    public ExecutionConfig executionConfig(
            @Value("${app.execution.enabled:true}") boolean enabled,
            @Value("${app.execution.mode:PAPER}") String mode,
            @Value("${app.execution.broker-name:paper}") String brokerName) {
        return ExecutionConfig.builder()
                .enabled(enabled)
                .mode(ExecutionMode.valueOf(mode.trim().toUpperCase()))
                .brokerName(brokerName)
                .build();
    }

    @Bean
    public ExecutionRuleRegistry executionRuleRegistry() {
        return new ExecutionRuleRegistry(BuiltInExecutionRules.all());
    }

    @Bean
    public BrokerAdapterRegistry brokerAdapterRegistry(Clock clock) {
        BrokerAdapterRegistry registry = new BrokerAdapterRegistry();
        registry.register(new PaperBrokerAdapter(clock));
        return registry;
    }

    @Bean
    public PortfolioService portfolioService(ExecutionConfig config) {
        return new PortfolioService(config);
    }

    @Bean
    public OrderStore orderStore(ExecutionConfig config) {
        return new OrderStore(config.maxRetainedOrders());
    }

    @Bean
    public ExecutionEventSink executionEventSink(MarketEventPublisher publisher) {
        return publisher::publish;
    }

    @Bean
    public ExecutionEngine executionEngine(ExecutionConfig config,
                                           ExecutionRuleRegistry rules,
                                           BrokerAdapterRegistry brokers,
                                           PortfolioService portfolio,
                                           OrderStore orders,
                                           ExecutionEventSink sink,
                                           Clock clock) {
        return new ExecutionEngine(config, rules, brokers, portfolio, orders, sink, clock);
    }

    @Bean
    public ExecutionMarketDataProvider executionMarketDataProvider(
            StrategyMarketDataProvider provider) {
        return (instrument, interval) -> provider.latestCandle(instrument, interval)
                .map(candle -> candle.close());
    }

    @Bean
    public ExecutionService executionService(ExecutionEngine engine,
                                             ExecutionMarketDataProvider provider,
                                             Clock clock) {
        return new ExecutionService(engine, provider, clock);
    }

    /**
     * Subscribes the service to the pipeline event bus. The bean's inferred destroy
     * method unsubscribes it on shutdown.
     */
    @Bean
    public MarketEventBus.Subscription executionSubscription(MarketEventBus bus,
                                                             ExecutionService service) {
        return bus.subscribe(service);
    }
}
