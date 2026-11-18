package com.tradingassistant.qualification;

import com.tradingassistant.pipeline.event.MarketEventBus;
import com.tradingassistant.pipeline.event.MarketEventPublisher;
import com.tradingassistant.qualification.config.TradeQualificationConfig;
import com.tradingassistant.qualification.domain.QualificationLedger;
import com.tradingassistant.qualification.engine.TradeQualificationEngine;
import com.tradingassistant.qualification.registry.BuiltInQualificationRules;
import com.tradingassistant.qualification.registry.QualificationRuleRegistry;
import com.tradingassistant.qualification.service.TradeQualificationService;
import com.tradingassistant.strategy.service.StrategyMarketDataProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.concurrent.Executor;

/**
 * Wires the trade qualification engine into the running pipeline.
 *
 * <p>Like the strategy layer, qualification is registered only when the broker is
 * enabled. The engine receives a direct executor by default: parallel rule
 * evaluation is available when a caller supplies an executor, but the application
 * never creates or owns a thread pool. The ledger is a bean because the service
 * writes it and the duplicate rule reads it; it is bounded and in-memory only.</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.broker.angel-one", name = "enabled", havingValue = "true")
public class QualificationConfiguration {

    @Bean
    public TradeQualificationConfig tradeQualificationConfig() {
        return TradeQualificationConfig.DEFAULT;
    }

    @Bean
    public QualificationLedger qualificationLedger() {
        return new QualificationLedger();
    }

    @Bean
    public QualificationRuleRegistry qualificationRuleRegistry(
            TradeQualificationConfig config,
            QualificationLedger ledger) {
        return new QualificationRuleRegistry(config,
                BuiltInQualificationRules.all(config, ledger));
    }

    @Bean
    public Executor qualificationExecutor() {
        return Runnable::run;
    }

    @Bean
    public TradeQualificationEngine tradeQualificationEngine(
            TradeQualificationConfig config,
            QualificationRuleRegistry registry,
            @Qualifier("qualificationExecutor") Executor executor) {
        return new TradeQualificationEngine(config, registry, executor);
    }

    @Bean
    public TradeQualificationService tradeQualificationService(
            TradeQualificationEngine engine,
            TradeQualificationConfig config,
            QualificationLedger ledger,
            StrategyMarketDataProvider provider,
            MarketEventPublisher publisher,
            Clock clock) {
        return new TradeQualificationService(engine, config, ledger, provider, publisher, clock);
    }

    /**
     * Subscribes the service to the pipeline event bus. The bean's inferred destroy
     * method unsubscribes it on shutdown.
     */
    @Bean
    public MarketEventBus.Subscription tradeQualificationSubscription(
            MarketEventBus bus,
            TradeQualificationService service) {
        return bus.subscribe(service);
    }
}
