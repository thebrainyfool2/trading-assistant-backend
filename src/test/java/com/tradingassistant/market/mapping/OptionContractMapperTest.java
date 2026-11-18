package com.tradingassistant.market.mapping;

import com.tradingassistant.market.MarketTestFixtures;
import com.tradingassistant.market.domain.Instrument;
import com.tradingassistant.market.domain.InstrumentType;
import com.tradingassistant.market.domain.OptionType;
import com.tradingassistant.market.exception.MarketDataMappingException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptionContractMapperTest {

    private final OptionContractMapper mapper = new OptionContractMapper();

    @Test
    void mapsOptionInstrumentAndIgnoresNonOption() {
        assertThat(mapper.fromInstrument(MarketTestFixtures.domainOption())).isPresent()
                .get()
                .satisfies(contract -> {
                    assertThat(contract.underlying()).isEqualTo("SBIN");
                    assertThat(contract.optionType()).isEqualTo(OptionType.CALL);
                    assertThat(contract.lotSize()).isEqualTo(500L);
                });
        assertThat(mapper.fromInstrument(MarketTestFixtures.domainEquity())).isEmpty();
    }

    @Test
    void rejectsMissingOptionFields() {
        Instrument missingStrike = new Instrument(
                MarketTestFixtures.OPTION_KEY, "S", "S", "S", InstrumentType.OPTION,
                BigDecimal.ONE, 1L, null, null, null, null,
                null, OptionType.CALL, "SBIN");

        assertThatThrownBy(() -> mapper.fromInstrument(missingStrike))
                .isInstanceOf(MarketDataMappingException.class)
                .hasMessageContaining("expiry");
    }
}
