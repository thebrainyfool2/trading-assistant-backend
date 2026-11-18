package com.tradingassistant.market.time;

import com.tradingassistant.market.domain.MarketStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketStatusResolverTest {

    @Test
    void resolvesPreOpenOpenAuctionAndClosedInMarketTimezone() {
        MarketStatusResolver resolver = resolver();

        assertThat(resolver.resolve(ist("2026-11-18T08:59:59"))).isEqualTo(MarketStatus.CLOSED);
        assertThat(resolver.resolve(ist("2026-11-18T09:00:00"))).isEqualTo(MarketStatus.PRE_OPEN);
        assertThat(resolver.resolve(ist("2026-11-18T09:14:59"))).isEqualTo(MarketStatus.PRE_OPEN);
        assertThat(resolver.resolve(ist("2026-11-18T09:15:00"))).isEqualTo(MarketStatus.OPEN);
        assertThat(resolver.resolve(ist("2026-11-18T15:29:59"))).isEqualTo(MarketStatus.OPEN);
        assertThat(resolver.resolve(ist("2026-11-18T15:30:00"))).isEqualTo(MarketStatus.AUCTION);
        assertThat(resolver.resolve(ist("2026-11-18T15:39:59"))).isEqualTo(MarketStatus.AUCTION);
        assertThat(resolver.resolve(ist("2026-11-18T15:40:00"))).isEqualTo(MarketStatus.CLOSED);
    }

    @Test
    void weekendsAreClosedAndNullIsUnknown() {
        MarketStatusResolver resolver = resolver();

        assertThat(resolver.resolve(ist("2026-11-21T10:00:00"))).isEqualTo(MarketStatus.CLOSED);
        assertThat(resolver.resolve(ist("2026-11-22T10:00:00"))).isEqualTo(MarketStatus.CLOSED);
        assertThat(resolver.resolve(null)).isEqualTo(MarketStatus.UNKNOWN);
    }

    @Test
    void convertsUtcInstantsToIndianMarketTimeAndBack() {
        MarketStatusResolver resolver = resolver();
        Instant utc = Instant.parse("2026-11-18T04:00:00Z");

        assertThat(resolver.toMarketTime(utc)).isEqualTo(LocalDateTime.of(2026, 11, 18, 9, 30));
        assertThat(resolver.toInstant(LocalDateTime.of(2026, 11, 18, 9, 30))).isEqualTo(utc);
        assertThatThrownBy(() -> resolver.toMarketTime(null)).isInstanceOf(NullPointerException.class);
    }

    private static MarketStatusResolver resolver() {
        return new MarketStatusResolver(Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC));
    }

    private static Instant ist(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(MarketStatusResolver.MARKET_ZONE).toInstant();
    }
}
