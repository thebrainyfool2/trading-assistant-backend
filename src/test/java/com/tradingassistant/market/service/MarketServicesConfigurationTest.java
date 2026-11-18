package com.tradingassistant.market.service;

import com.tradingassistant.broker.api.BrokerProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MarketServicesConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MarketServicesConfiguration.class)
            .withBean(BrokerProvider.class, () -> mock(BrokerProvider.class));

    @Test
    void disabledBrokerDoesNotCreateMarketServices() {
        runner.withPropertyValues("app.broker.angel-one.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(MarketDataService.class));
    }

    @Test
    void enabledBrokerCreatesTheDomainFacade() {
        runner.withPropertyValues("app.broker.angel-one.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MarketDataService.class);
                    assertThat(context).hasSingleBean(InstrumentService.class);
                    assertThat(context).hasSingleBean(QuoteService.class);
                    assertThat(context).hasSingleBean(HistoricalDataService.class);
                });
    }
}
