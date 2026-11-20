package com.tradingassistant.platform.live;

import com.tradingassistant.market.domain.Currency;
import com.tradingassistant.market.domain.Instrument;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.InstrumentType;
import com.tradingassistant.market.domain.MarketDepth;
import com.tradingassistant.market.domain.MarketStatus;
import com.tradingassistant.market.domain.OptionContract;
import com.tradingassistant.market.domain.Quote;
import com.tradingassistant.market.domain.QuoteMode;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.market.service.MarketDataService;
import com.tradingassistant.options.OptionsTestFixtures;
import com.tradingassistant.options.chain.OptionChainBuilder;
import com.tradingassistant.options.service.InMemoryOptionChainProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveOptionChainServiceTest {

    private static final InstrumentKey SPOT =
            new InstrumentKey(Segment.NSE_CASH, "2885");
    private static final Instant NOW = OptionsTestFixtures.START;

    private final MarketDataService marketData = mock(MarketDataService.class);
    private InMemoryOptionChainProvider provider;
    private LiveOptionChainService service;

    @BeforeEach
    void setUp() {
        provider = new InMemoryOptionChainProvider(
                new OptionChainBuilder(OptionsTestFixtures.CONFIG));
        service = new LiveOptionChainService(marketData, provider,
                Clock.fixed(NOW, ZoneOffset.UTC), true, 2, 0, 50);
    }

    @Test
    void stagesNearestExpiryAndClosestStrikesFromTheInstrumentMaster() {
        List<OptionContract> contracts = OptionsTestFixtures.ladder("SBIN",
                OptionsTestFixtures.EXPIRY, OptionsTestFixtures.strikes("460", "540", "10"));
        Map<InstrumentKey, Quote> optionQuotes = OptionsTestFixtures.quoted(contracts,
                "500", "0.25", 1_000L, 500L, "0.05", "0.10");

        when(marketData.instruments()).thenReturn(instruments(contracts));
        when(marketData.quotes(any(QuoteMode.class), anyList())).thenAnswer(invocation -> {
            QuoteMode mode = invocation.getArgument(0);
            List<InstrumentKey> keys = invocation.getArgument(1);
            if (mode == QuoteMode.LTP) {
                return keys.contains(SPOT) ? List.of(spotQuote()) : List.of();
            }
            return keys.stream().map(optionQuotes::get).filter(java.util.Objects::nonNull).toList();
        });

        service.refresh(Set.of(SPOT));

        assertThat(provider.snapshotFor(SPOT, NOW)).isPresent();
        var snapshot = provider.snapshotFor(SPOT, NOW).orElseThrow();
        assertThat(snapshot.underlying()).isEqualTo("SBIN");
        assertThat(snapshot.spotPrice()).isEqualByComparingTo("500");
        assertThat(snapshot.nearestExpiryDate()).contains(OptionsTestFixtures.EXPIRY);
        assertThat(snapshot.legs()).isNotEmpty();
        assertThat(snapshot.legs()).allSatisfy(leg ->
                assertThat(leg.strike()).isBetween(new BigDecimal("480"), new BigDecimal("520")));
    }

    @Test
    void respectsTheRefreshInterval() {
        when(marketData.instruments()).thenReturn(List.of(spot()));
        when(marketData.quotes(any(QuoteMode.class), anyList())).thenReturn(List.of());

        service = new LiveOptionChainService(marketData, provider,
                Clock.fixed(NOW, ZoneOffset.UTC), true, 2, 60_000, 50);
        service.refresh(Set.of(SPOT));
        service.refresh(Set.of(SPOT));

        verify(marketData, times(1)).instruments();
    }

    @Test
    void skipsAnInstrumentWithoutOptionContracts() {
        when(marketData.instruments()).thenReturn(List.of(spot()));

        service.refresh(Set.of(SPOT));

        assertThat(provider.snapshotFor(SPOT, NOW)).isEmpty();
    }

    private static List<Instrument> instruments(List<OptionContract> contracts) {
        List<Instrument> values = new java.util.ArrayList<>();
        values.add(spot());
        values.addAll(contracts.stream().map(OptionContract::instrument).toList());
        return List.copyOf(values);
    }

    private static Instrument spot() {
        return new Instrument(SPOT, "SBIN-EQ", "SBIN-EQ", "SBIN",
                InstrumentType.EQUITY, new BigDecimal("0.05"), 1L, null, null,
                Currency.INR, null, null, null, null);
    }

    private static Quote spotQuote() {
        return new Quote(SPOT, "SBIN-EQ", new BigDecimal("500"), null, null, null,
                null, null, 1_000L, null, null, BigDecimal.ONE, null, null, null,
                NOW, MarketStatus.OPEN, null, null, null, null, MarketDepth.empty());
    }
}
