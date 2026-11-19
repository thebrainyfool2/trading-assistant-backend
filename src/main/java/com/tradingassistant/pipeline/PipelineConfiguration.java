package com.tradingassistant.pipeline;

import com.tradingassistant.indicator.engine.IndicatorEngine;
import com.tradingassistant.pipeline.aggregation.AggregationPolicy;
import com.tradingassistant.pipeline.calendar.MarketCalendarRegistry;
import com.tradingassistant.pipeline.event.MarketEventBus;
import com.tradingassistant.pipeline.event.MarketEventPublisher;
import com.tradingassistant.pipeline.event.MarketEventSessionListener;
import com.tradingassistant.pipeline.session.IndicatorSessionRegistry;
import com.tradingassistant.pipeline.session.SessionLifecycleListener;
import com.tradingassistant.pipeline.session.SessionRegistryPolicy;
import com.tradingassistant.strategy.service.InMemoryStrategyMarketDataProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wires the runtime pipeline with the operational Phase 1 endpoints untouched.
 *
 * <p>The pipeline is registered only when the broker layer is enabled, matching
 * the Phase 3 market services: without a market feed there is nothing to process,
 * and the health and system endpoints must keep starting with an empty
 * configuration. Every collaborator is a bean, so a deployment can replace the
 * calendar, registry policy, aggregation policy or event publisher without
 * editing the pipeline.</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.broker.angel-one", name = "enabled", havingValue = "true")
public class PipelineConfiguration {

    @Bean
    public MarketCalendarRegistry marketCalendarRegistry() {
        return MarketCalendarRegistry.defaults();
    }

    @Bean
    public MarketEventBus marketEventBus() {
        return new MarketEventBus();
    }

    @Bean
    public IndicatorEngine indicatorEngine() {
        return IndicatorEngine.standard();
    }

    @Bean
    public SessionRegistryPolicy sessionRegistryPolicy() {
        return SessionRegistryPolicy.defaults();
    }

    @Bean
    public AggregationPolicy aggregationPolicy() {
        return AggregationPolicy.defaults();
    }

    @Bean
    public SessionLifecycleListener sessionLifecycleListener(
            MarketEventPublisher marketEventPublisher,
            Clock clock) {
        return new MarketEventSessionListener(marketEventPublisher, clock);
    }

    @Bean
    public IndicatorSessionRegistry indicatorSessionRegistry(
            IndicatorEngine indicatorEngine,
            SessionRegistryPolicy sessionRegistryPolicy,
            Clock clock,
            SessionLifecycleListener sessionLifecycleListener) {
        return new IndicatorSessionRegistry(
                indicatorEngine, sessionRegistryPolicy, clock, sessionLifecycleListener);
    }

    @Bean
    public MarketProcessingPipeline marketProcessingPipeline(
            IndicatorSessionRegistry indicatorSessionRegistry,
            MarketEventPublisher marketEventPublisher,
            MarketCalendarRegistry marketCalendarRegistry,
            AggregationPolicy aggregationPolicy,
            Clock clock,
            java.util.List<MarketDataObserver> observers) {
        return new MarketProcessingPipeline(
                indicatorSessionRegistry,
                marketEventPublisher,
                marketCalendarRegistry,
                aggregationPolicy,
                clock,
                observers);
    }

    /**
     * Keeps the strategy/risk staging current before an indicator event is
     * published. The pipeline calls this synchronously, so an evaluation that
     * is triggered by the event never sees stale candle data.
     */
    @Bean
    public MarketDataObserver strategyMarketDataObserver(
            ObjectProvider<InMemoryStrategyMarketDataProvider> providerProvider) {
        InMemoryStrategyMarketDataProvider provider = providerProvider.getIfAvailable();
        if (provider == null) {
            return new MarketDataObserver() {
            };
        }
        return new MarketDataObserver() {
            @Override
            public void onCandle(com.tradingassistant.market.domain.Candle candle) {
                provider.updateCandle(candle);
            }

            @Override
            public void onIndicators(com.tradingassistant.market.domain.Candle candle,
                                     java.util.Map<String, com.tradingassistant.indicator.domain.IndicatorResult> indicators) {
                provider.updateIndicators(candle.instrument(), candle.interval(), indicators);
            }
        };
    }

    @Bean
    public MarketSessionLifecycleManager marketSessionLifecycleManager(
            MarketProcessingPipeline marketProcessingPipeline,
            IndicatorSessionRegistry indicatorSessionRegistry,
            Clock clock) {
        return new MarketSessionLifecycleManager(
                marketProcessingPipeline, indicatorSessionRegistry, clock);
    }
}
