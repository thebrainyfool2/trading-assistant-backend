package com.tradingassistant.market.live;

import com.tradingassistant.market.domain.Instrument;
import com.tradingassistant.market.domain.OptionContract;
import com.tradingassistant.market.service.MarketDataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in check that the real instrument master can cross the domain boundary.
 * It requires the same environment credentials and request metadata as the Phase
 * 2 live test and never prints tokens or client metadata.
 */
@SpringBootTest(properties = "app.broker.angel-one.enabled=true")
@EnabledIfEnvironmentVariable(named = "SMARTAPI_LIVE_TESTS", matches = "true")
class MarketDomainLiveIntegrationTest {

    @Autowired
    private MarketDataService marketData;

    @Test
    void mapsRealInstrumentsAndOptionContractsIntoTheDomain() {
        List<Instrument> instruments = marketData.instruments();
        List<OptionContract> options = marketData.optionContracts();

        assertThat(instruments).hasSizeGreaterThan(1000);
        assertThat(instruments).allSatisfy(instrument -> {
            assertThat(instrument.symbolToken()).isNotBlank();
            assertThat(instrument.tradingSymbol()).isNotBlank();
            assertThat(instrument.segment()).isNotNull();
        });
        assertThat(options).isNotEmpty();
    }
}
