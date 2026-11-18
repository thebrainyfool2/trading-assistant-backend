package com.tradingassistant.market.service;

import com.tradingassistant.broker.api.BrokerProvider;
import com.tradingassistant.broker.model.CandleRequest;
import com.tradingassistant.market.MarketTestFixtures;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.exception.MarketDataValidationException;
import com.tradingassistant.market.mapping.CandleMapper;
import com.tradingassistant.market.time.MarketStatusResolver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HistoricalDataServiceTest {

    private final BrokerProvider broker = mock(BrokerProvider.class);
    private final MarketStatusResolver marketTime = new MarketStatusResolver(
            Clock.fixed(MarketTestFixtures.MARKET_OPEN, ZoneOffset.UTC));
    private final HistoricalDataService service = new HistoricalDataService(
            broker, new CandleMapper(), marketTime);

    @Test
    void convertsUtcRangeToIndianMarketTimeAndMapsCandles() {
        when(broker.candles(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(MarketTestFixtures.providerCandle()));

        List<Candle> candles = service.candles(
                MarketTestFixtures.KEY,
                Interval.ONE_DAY,
                Instant.parse("2026-11-18T03:45:00Z"),
                Instant.parse("2026-11-18T10:00:00Z"));

        assertThat(candles).hasSize(1);
        assertThat(candles.getFirst().timestamp()).isEqualTo(Instant.parse("2026-11-18T03:45:00Z"));

        ArgumentCaptor<CandleRequest> captor = ArgumentCaptor.forClass(CandleRequest.class);
        verify(broker).candles(captor.capture());
        assertThat(captor.getValue().from()).isEqualTo(LocalDateTime.of(2026, 11, 18, 9, 15));
        assertThat(captor.getValue().to()).isEqualTo(LocalDateTime.of(2026, 11, 18, 15, 30));
        assertThat(captor.getValue().interval()).isEqualTo(
                com.tradingassistant.broker.model.CandleInterval.ONE_DAY);
    }

    @Test
    void rejectsInvertedRangeAndUnsupportedSpan() {
        assertThatThrownBy(() -> service.candles(
                MarketTestFixtures.KEY, Interval.ONE_DAY,
                Instant.parse("2026-11-18T10:00:00Z"), Instant.parse("2026-11-18T09:00:00Z")))
                .isInstanceOf(MarketDataValidationException.class)
                .hasMessageContaining("after");

        assertThatThrownBy(() -> service.candles(
                MarketTestFixtures.KEY, Interval.ONE_MINUTE,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-02-01T00:00:00Z")))
                .isInstanceOf(MarketDataValidationException.class)
                .hasMessageContaining("invalid");
    }
}
