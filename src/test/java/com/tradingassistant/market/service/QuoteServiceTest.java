package com.tradingassistant.market.service;

import com.tradingassistant.broker.api.BrokerProvider;
import com.tradingassistant.broker.exception.BrokerUnavailableException;
import com.tradingassistant.market.MarketTestFixtures;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.MarketStatus;
import com.tradingassistant.market.domain.Quote;
import com.tradingassistant.market.domain.QuoteMode;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.market.exception.MarketDataUnavailableException;
import com.tradingassistant.market.exception.MarketDataValidationException;
import com.tradingassistant.market.mapping.MarketDepthMapper;
import com.tradingassistant.market.mapping.QuoteMapper;
import com.tradingassistant.market.time.MarketStatusResolver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuoteServiceTest {

    private final BrokerProvider broker = mock(BrokerProvider.class);
    private final MarketStatusResolver marketTime = new MarketStatusResolver(
            Clock.fixed(MarketTestFixtures.MARKET_OPEN, ZoneOffset.UTC));
    private final QuoteService service = new QuoteService(
            broker,
            new QuoteMapper(new MarketDepthMapper()),
            marketTime);

    @Test
    void groupsByProviderExchangeAndMapsDomainQuote() {
        when(broker.quotes(any(), anyMap())).thenReturn(List.of(MarketTestFixtures.providerQuote()));

        List<Quote> quotes = service.quotes(
                QuoteMode.FULL,
                List.of(
                        new InstrumentKey(Segment.NSE_CASH, "3045"),
                        new InstrumentKey(Segment.NSE_FO, "12345"),
                        new InstrumentKey(Segment.NSE_CASH, "3045")));

        assertThat(quotes).hasSize(1);
        assertThat(quotes.getFirst().marketStatus()).isEqualTo(MarketStatus.OPEN);
        assertThat(quotes.getFirst().timestamp()).isEqualTo(MarketTestFixtures.MARKET_OPEN);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<com.tradingassistant.broker.model.Exchange, List<String>>> captor =
                ArgumentCaptor.forClass(Map.class);
        verify(broker).quotes(
                org.mockito.ArgumentMatchers.eq(com.tradingassistant.broker.model.QuoteMode.FULL),
                captor.capture());
        assertThat(captor.getValue()).containsEntry(
                com.tradingassistant.broker.model.Exchange.NSE, List.of("3045"));
        assertThat(captor.getValue()).containsEntry(
                com.tradingassistant.broker.model.Exchange.NFO, List.of("12345"));
    }

    @Test
    void rejectsEmptyAndOversizedRequests() {
        assertThatThrownBy(() -> service.quotes(QuoteMode.LTP, List.of()))
                .isInstanceOf(MarketDataValidationException.class);
        assertThatThrownBy(() -> service.quotes(
                QuoteMode.LTP,
                IntStream.rangeClosed(1, 51)
                        .mapToObj(index -> new InstrumentKey(Segment.NSE_CASH, String.valueOf(index)))
                        .toList()))
                .isInstanceOf(MarketDataValidationException.class)
                .hasMessageContaining("50");
    }

    @Test
    void rejectsUnsupportedNcoQuoteRouting() {
        assertThatThrownBy(() -> service.quotes(
                QuoteMode.LTP,
                List.of(new InstrumentKey(Segment.NSE_COM, "26074"))))
                .isInstanceOf(MarketDataValidationException.class)
                .hasMessageContaining("NCO");
    }

    @Test
    void translatesProviderFailure() {
        when(broker.quotes(any(), anyMap())).thenThrow(new BrokerUnavailableException("down"));

        assertThatThrownBy(() -> service.quotes(
                QuoteMode.LTP, List.of(MarketTestFixtures.KEY)))
                .isInstanceOf(MarketDataUnavailableException.class)
                .hasMessageNotContaining("down");
    }
}
