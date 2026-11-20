package com.tradingassistant.platform.live;

import com.tradingassistant.market.domain.Currency;
import com.tradingassistant.market.domain.Instrument;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.InstrumentType;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.market.service.InstrumentService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveInstrumentResolverTest {

    private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");
    private final InstrumentService instruments = mock(InstrumentService.class);
    private final LiveInstrumentResolver resolver = new LiveInstrumentResolver(instruments,
            Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(10));

    @Test
    void numericTokensPassThroughWithoutLoadingTheMaster() {
        assertThat(resolver.resolve("NSE_CASH:2885"))
                .contains(new InstrumentKey(Segment.NSE_CASH, "2885"));

        verify(instruments, never()).instruments();
    }

    @Test
    void resolvesAnIndexAliasAndPrefersTheIndexOverOtherMatches() {
        when(instruments.instruments()).thenReturn(List.of(
                instrument("2885", "RELIANCE", "RELIANCE", InstrumentType.EQUITY,
                        Segment.NSE_CASH, null),
                instrument("26000", "NIFTY", "NIFTY 50", InstrumentType.INDEX,
                        Segment.NSE_CASH, null),
                instrument("26001", "NIFTY", "NIFTY", InstrumentType.EQUITY,
                        Segment.NSE_CASH, null)));

        assertThat(resolver.resolve("NSE_CASH:NIFTY"))
                .contains(new InstrumentKey(Segment.NSE_CASH, "26000"));
    }

    @Test
    void resolvesTheNearestNonExpiredFuture() {
        when(instruments.instruments()).thenReturn(List.of(
                future("1001", "NIFTY24NOVFUT", "2026-11-20"),
                future("1002", "NIFTY26NOVFUT", "2026-11-26"),
                future("1003", "NIFTY31DECFUT", "2026-12-31"),
                future("1000", "NIFTY30OCTFUT", "2026-10-29")));

        assertThat(resolver.resolve("NSE_FO:NIFTY"))
                .contains(new InstrumentKey(Segment.NSE_FO, "1001"));
    }

    @Test
    void doesNotGuessAnOptionContract() {
        when(instruments.instruments()).thenReturn(List.of(
                instrument("2001", "NIFTY26NOV25700CE", "NIFTY", InstrumentType.OPTION,
                        Segment.NSE_FO, LocalDate.of(2026, 11, 26))));

        assertThat(resolver.resolve("NSE_FO:NIFTY")).isEmpty();
    }

    @Test
    void unknownAliasIsEmpty() {
        when(instruments.instruments()).thenReturn(List.of());

        assertThat(resolver.resolve("NSE_CASH:DOES_NOT_EXIST")).isEmpty();
    }

    private static Instrument instrument(String token, String symbol, String tradingSymbol,
                                         InstrumentType type, Segment segment,
                                         LocalDate expiry) {
        return new Instrument(new InstrumentKey(segment, token), symbol, tradingSymbol,
                tradingSymbol, type, new BigDecimal("0.05"), 1L, null, null,
                Currency.INR, expiry, null, null, null);
    }

    private static Instrument future(String token, String tradingSymbol, String expiry) {
        return instrument(token, "NIFTY", tradingSymbol, InstrumentType.FUTURE,
                Segment.NSE_FO, LocalDate.parse(expiry));
    }
}
