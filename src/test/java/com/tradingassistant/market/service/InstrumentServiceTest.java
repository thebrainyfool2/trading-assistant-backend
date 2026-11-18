package com.tradingassistant.market.service;

import com.tradingassistant.broker.api.BrokerProvider;
import com.tradingassistant.broker.exception.BrokerAuthenticationException;
import com.tradingassistant.broker.exception.BrokerError;
import com.tradingassistant.broker.model.InstrumentMaster;
import com.tradingassistant.market.MarketTestFixtures;
import com.tradingassistant.market.domain.Instrument;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.OptionContract;
import com.tradingassistant.market.exception.MarketDataAuthenticationException;
import com.tradingassistant.market.mapping.InstrumentMapper;
import com.tradingassistant.market.mapping.OptionContractMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InstrumentServiceTest {

    private final BrokerProvider broker = mock(BrokerProvider.class);
    private final InstrumentService service = new InstrumentService(
            broker, new InstrumentMapper(), new OptionContractMapper());

    @Test
    void mapsEquityAndOptionsAndFindsByKey() {
        when(broker.instruments()).thenReturn(new InstrumentMaster(
                List.of(MarketTestFixtures.providerEquity(), MarketTestFixtures.providerOption()),
                Instant.parse("2026-11-18T04:00:00Z")));

        List<Instrument> instruments = service.instruments();
        Optional<Instrument> found = service.find(MarketTestFixtures.KEY);
        List<OptionContract> options = service.optionContracts();

        assertThat(instruments).hasSize(2);
        assertThat(found).isPresent();
        assertThat(found.orElseThrow().symbolToken()).isEqualTo("3045");
        assertThat(options).hasSize(1);
        assertThat(options.getFirst().underlying()).isEqualTo("SBIN");
    }

    @Test
    void translatesBrokerAuthenticationFailure() {
        when(broker.instruments()).thenThrow(new BrokerAuthenticationException(
                BrokerError.AUTHENTICATION_FAILED, "rejected"));

        assertThatThrownBy(service::instruments)
                .isInstanceOf(MarketDataAuthenticationException.class)
                .hasMessageNotContaining("rejected");
    }

    @Test
    void rejectsNullLookupKey() {
        assertThatThrownBy(() -> service.find((InstrumentKey) null))
                .hasMessageContaining("instrument key");
    }
}
