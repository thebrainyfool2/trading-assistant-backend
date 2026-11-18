package com.tradingassistant.market.service;

import com.tradingassistant.broker.api.BrokerProvider;
import com.tradingassistant.market.mapping.CandleMapper;
import com.tradingassistant.market.mapping.InstrumentMapper;
import com.tradingassistant.market.mapping.MarketDepthMapper;
import com.tradingassistant.market.mapping.OptionContractMapper;
import com.tradingassistant.market.mapping.QuoteMapper;
import com.tradingassistant.market.mapping.TickMapper;
import com.tradingassistant.market.time.MarketStatusResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * The application market services are available only when a broker provider is
 * configured. The operational Phase 1 endpoints continue to start without them.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.broker.angel-one", name = "enabled", havingValue = "true")
public class MarketServicesConfiguration {

    @Bean
    public Clock marketDataClock() {
        return Clock.systemUTC();
    }

    @Bean
    public MarketStatusResolver marketStatusResolver(Clock marketDataClock) {
        return new MarketStatusResolver(marketDataClock);
    }

    @Bean
    public InstrumentMapper instrumentMapper() {
        return new InstrumentMapper();
    }

    @Bean
    public OptionContractMapper optionContractMapper() {
        return new OptionContractMapper();
    }

    @Bean
    public MarketDepthMapper marketDepthMapper() {
        return new MarketDepthMapper();
    }

    @Bean
    public QuoteMapper quoteMapper(MarketDepthMapper marketDepthMapper) {
        return new QuoteMapper(marketDepthMapper);
    }

    @Bean
    public CandleMapper candleMapper() {
        return new CandleMapper();
    }

    @Bean
    public TickMapper tickMapper(MarketDepthMapper marketDepthMapper) {
        return new TickMapper(marketDepthMapper);
    }

    @Bean
    public InstrumentService instrumentService(BrokerProvider broker,
                                               InstrumentMapper instrumentMapper,
                                               OptionContractMapper optionContractMapper) {
        return new InstrumentService(broker, instrumentMapper, optionContractMapper);
    }

    @Bean
    public QuoteService quoteService(BrokerProvider broker,
                                     QuoteMapper quoteMapper,
                                     MarketStatusResolver marketStatusResolver) {
        return new QuoteService(broker, quoteMapper, marketStatusResolver);
    }

    @Bean
    public HistoricalDataService historicalDataService(BrokerProvider broker,
                                                       CandleMapper candleMapper,
                                                       MarketStatusResolver marketStatusResolver) {
        return new HistoricalDataService(broker, candleMapper, marketStatusResolver);
    }

    @Bean
    public MarketDataService marketDataService(BrokerProvider broker,
                                               InstrumentService instrumentService,
                                               QuoteService quoteService,
                                               HistoricalDataService historicalDataService,
                                               TickMapper tickMapper,
                                               MarketStatusResolver marketStatusResolver) {
        return new MarketDataService(
                broker,
                instrumentService,
                quoteService,
                historicalDataService,
                tickMapper,
                marketStatusResolver);
    }
}
