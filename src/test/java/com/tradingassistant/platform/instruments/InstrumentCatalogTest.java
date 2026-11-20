package com.tradingassistant.platform.instruments;

import com.tradingassistant.market.domain.Currency;
import com.tradingassistant.market.domain.Instrument;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.InstrumentType;
import com.tradingassistant.market.domain.OptionType;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.market.service.InstrumentService;
import com.tradingassistant.options.OptionsTestFixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InstrumentCatalogTest {

    private final InstrumentService instruments = mock(InstrumentService.class);
    private final Clock clock = Clock.fixed(OptionsTestFixtures.START, ZoneOffset.UTC);

    @Test
    void cachesTheInstrumentMaster() {
        when(instruments.instruments()).thenReturn(List.of(equity("RELIANCE-EQ",
                "RELIANCE", "RELIANCE")));
        InstrumentCatalog catalog = new InstrumentCatalog(instruments, clock);

        catalog.all();
        catalog.all();

        verify(instruments, times(1)).instruments();
    }

    @Test
    void searchFindsCashEquityByNameAndExcludesOptionsByDefault() {
        Instrument reliance = equity("RELIANCE-EQ", "RELIANCE", "RELIANCE");
        Instrument option = OptionsTestFixtures.contract("RELIANCE",
                OptionsTestFixtures.EXPIRY, "3000", OptionType.CALL).instrument();
        InstrumentCatalog catalog = new InstrumentCatalog(instruments, clock);
        when(instruments.instruments()).thenReturn(List.of(option, reliance));
        InstrumentSearchService search = new InstrumentSearchService(catalog);

        assertThat(search.search("reliance", null, null, 10))
                .extracting(Instrument::symbol)
                .containsExactly("RELIANCE-EQ");
    }

    @Test
    void resolvesTheListedOptionByTradingSymbol() {
        Instrument option = OptionsTestFixtures.contract("SBIN",
                OptionsTestFixtures.EXPIRY, "500", OptionType.CALL).instrument();
        Instrument spot = equity("SBIN-EQ", "SBIN", "SBIN");
        when(instruments.instruments()).thenReturn(List.of(spot, option));
        InstrumentCatalog catalog = new InstrumentCatalog(instruments, clock);

        assertThat(catalog.findOption(new InstrumentKey(Segment.NSE_CASH, "3045"),
                option.tradingSymbol(), new BigDecimal("500"),
                OptionsTestFixtures.EXPIRY, OptionType.CALL))
                .contains(option);
    }

    private static Instrument equity(String symbol, String displayName, String token) {
        return new Instrument(new InstrumentKey(Segment.NSE_CASH, token),
                symbol, symbol, displayName, InstrumentType.EQUITY,
                new BigDecimal("0.05"), 1L, null, null, Currency.INR,
                null, null, null, null);
    }
}
