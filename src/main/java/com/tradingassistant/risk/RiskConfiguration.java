package com.tradingassistant.risk;

import com.tradingassistant.pipeline.event.MarketEventBus;
import com.tradingassistant.pipeline.event.MarketEventPublisher;
import com.tradingassistant.risk.config.RiskEngineConfig;
import com.tradingassistant.risk.engine.RiskEngine;
import com.tradingassistant.risk.registry.BuiltInRiskRules;
import com.tradingassistant.risk.registry.RiskRuleRegistry;
import com.tradingassistant.risk.service.RiskAssessmentService;
import com.tradingassistant.risk.service.RiskMarketDataProvider;
import com.tradingassistant.strategy.service.StrategyMarketDataProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.concurrent.Executor;

/**
 * Wires the risk engine into the running pipeline.
 *
 * <p>Like the earlier evaluation layers, risk assessment is registered only when
 * the broker is enabled. The engine receives a direct executor by default:
 * parallel rule evaluation is available when a caller supplies an executor, but the
 * application never creates or owns a thread pool.</p>
 *
 * <p>The candle and indicator staging of the strategy layer is adapted to the
 * risk-specific provider contract, so the risk package does not depend on the
 * strategy service implementation.</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.broker.angel-one", name = "enabled", havingValue = "true")
public class RiskConfiguration {

    @Bean
    public RiskEngineConfig riskEngineConfig() {
        return RiskEngineConfig.DEFAULT;
    }

    @Bean
    public RiskRuleRegistry riskRuleRegistry() {
        return new RiskRuleRegistry(BuiltInRiskRules.all());
    }

    @Bean
    public Executor riskExecutor() {
        return Runnable::run;
    }

    @Bean
    public RiskEngine riskEngine(RiskEngineConfig config,
                                 RiskRuleRegistry registry,
                                 @Qualifier("riskExecutor") Executor executor) {
        return new RiskEngine(config, registry, executor);
    }

    @Bean
    public RiskMarketDataProvider riskMarketDataProvider(StrategyMarketDataProvider provider) {
        return new RiskMarketDataProvider() {
            @Override
            public java.util.Optional<com.tradingassistant.market.domain.Candle> latestCandle(
                    com.tradingassistant.market.domain.InstrumentKey instrument,
                    com.tradingassistant.market.domain.Interval interval) {
                return provider.latestCandle(instrument, interval);
            }

            @Override
            public java.util.Map<String, com.tradingassistant.indicator.domain.IndicatorResult>
                    latestIndicators(com.tradingassistant.market.domain.InstrumentKey instrument,
                                     com.tradingassistant.market.domain.Interval interval) {
                return provider.latestIndicators(instrument, interval);
            }
        };
    }

    @Bean
    public RiskAssessmentService riskAssessmentService(
            RiskEngine engine,
            RiskEngineConfig config,
            RiskMarketDataProvider provider,
            MarketEventPublisher publisher,
            Clock clock) {
        return new RiskAssessmentService(engine, config, provider, publisher, clock);
    }

    /**
     * Subscribes the service to the pipeline event bus. The bean's inferred destroy
     * method unsubscribes it on shutdown.
     */
    @Bean
    public MarketEventBus.Subscription riskAssessmentSubscription(
            MarketEventBus bus,
            RiskAssessmentService service) {
        return bus.subscribe(service);
    }
}
