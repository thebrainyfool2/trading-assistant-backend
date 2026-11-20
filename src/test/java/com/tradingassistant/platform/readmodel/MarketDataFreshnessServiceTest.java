package com.tradingassistant.platform.readmodel;

import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.MarketDepth;
import com.tradingassistant.market.domain.MarketStatus;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.market.domain.Tick;
import com.tradingassistant.market.domain.TickMode;
import com.tradingassistant.pipeline.calendar.MarketCalendarRegistry;
import com.tradingassistant.pipeline.event.CandleCompletedEvent;
import com.tradingassistant.pipeline.event.TickReceivedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketDataFreshnessServiceTest {

    private static final InstrumentKey RELIANCE = new InstrumentKey(Segment.NSE_CASH, "2885");

    @Test
    void reportsFreshDataAndTheOpenSession() {
        MarketDataFreshnessService service = service("2026-11-19T04:30:00Z",
                MarketCalendarRegistry.defaults());
        service.onEvent(new TickReceivedEvent(tick("2026-11-19T04:29:30Z"), at("2026-11-19T04:29:30Z")));
        service.onEvent(new CandleCompletedEvent(candle("2026-11-19T04:25:00Z"),
                at("2026-11-19T04:25:00Z")));

        MarketDataFreshnessService.Freshness freshness = service.snapshot().getFirst();

        assertThat(freshness.status()).isEqualTo(MarketDataFreshnessService.FreshnessStatus.FRESH);
        assertThat(freshness.session()).isEqualTo(MarketDataFreshnessService.MarketSessionState.OPEN);
        assertThat(freshness.tickAgeSeconds()).isEqualTo(30);
        assertThat(freshness.candleAgeSeconds()).isEqualTo(300);
        assertThat(freshness.latestPrice()).isEqualByComparingTo("2500.50");
    }

    @Test
    void reportsStaleDataWhenTheTickAgeExceedsPolicy() {
        MarketDataFreshnessService service = service("2026-11-19T04:30:00Z",
                MarketCalendarRegistry.defaults());
        service.onEvent(new TickReceivedEvent(tick("2026-11-19T04:20:00Z"), at("2026-11-19T04:20:00Z")));

        assertThat(service.snapshot().getFirst().status())
                .isEqualTo(MarketDataFreshnessService.FreshnessStatus.STALE);
    }

    @Test
    void reportsUnknownBeforeAnyObservation() {
        MarketDataFreshnessService service = service("2026-11-19T04:30:00Z",
                MarketCalendarRegistry.defaults());

        assertThat(service.snapshot()).isEmpty();
        assertThat(service.freshness(RELIANCE)).isEmpty();
    }

    @Test
    void reportsClosedSessionWithoutPretendingTheMarketIsOpen() {
        MarketDataFreshnessService service = service("2026-11-19T10:30:00Z",
                MarketCalendarRegistry.defaults());
        service.onEvent(new TickReceivedEvent(tick("2026-11-19T10:29:30Z"), at("2026-11-19T10:29:30Z")));

        MarketDataFreshnessService.Freshness freshness = service.snapshot().getFirst();
        assertThat(freshness.session()).isEqualTo(MarketDataFreshnessService.MarketSessionState.CLOSED);
    }

    private static MarketDataFreshnessService service(String now,
                                                       MarketCalendarRegistry calendars) {
        ObjectProvider<MarketCalendarRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(calendars);
        return new MarketDataFreshnessService(
                Clock.fixed(Instant.parse(now), ZoneOffset.UTC),
                Duration.ofMinutes(1), Duration.ofMinutes(10), provider);
    }

    private static Tick tick(String receivedAt) {
        Instant time = at(receivedAt);
        return new Tick(TickMode.QUOTE, RELIANCE, 1L, time, time,
                new BigDecimal("2500.50"), 10L, new BigDecimal("2500.00"), 1000L,
                100L, 200L, new BigDecimal("2490.00"), new BigDecimal("2510.00"),
                new BigDecimal("2480.00"), new BigDecimal("2495.00"), time, 500L,
                MarketDepth.empty(), null, null, null, null, MarketStatus.OPEN);
    }

    private static Candle candle(String timestamp) {
        return new Candle(RELIANCE, Interval.FIVE_MINUTE, at(timestamp),
                new BigDecimal("2500.00"), new BigDecimal("2502.00"),
                new BigDecimal("2498.00"), new BigDecimal("2500.50"), 1000L);
    }

    private static Instant at(String value) {
        return Instant.parse(value);
    }
}
