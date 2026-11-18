package com.tradingassistant.market.mapping;

import com.tradingassistant.broker.model.Exchange;
import com.tradingassistant.broker.model.FeedMode;
import com.tradingassistant.broker.model.FeedTick;
import com.tradingassistant.market.MarketTestFixtures;
import com.tradingassistant.market.domain.MarketStatus;
import com.tradingassistant.market.domain.Tick;
import com.tradingassistant.market.domain.TickMode;
import com.tradingassistant.market.exception.MarketDataMappingException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TickMapperTest {

    private final TickMapper mapper = new TickMapper(new MarketDepthMapper());

    @Test
    void mapsSnapshotWithReceiptTimeAndDepth() {
        Instant receivedAt = Instant.parse("2026-11-18T04:00:02Z");
        Tick tick = mapper.toDomain(MarketTestFixtures.providerTick(), receivedAt, MarketStatus.OPEN);

        assertThat(tick.mode()).isEqualTo(TickMode.SNAPSHOT);
        assertThat(tick.instrument().symbolToken()).isEqualTo("3045");
        assertThat(tick.receivedAt()).isEqualTo(receivedAt);
        assertThat(tick.exchangeTimestamp()).isEqualTo(Instant.parse("2026-11-18T04:00:01Z"));
        assertThat(tick.lastTradedPrice()).isEqualByComparingTo("800.25");
        assertThat(tick.depth().buy()).hasSize(1);
        assertThat(tick.marketStatus()).isEqualTo(MarketStatus.OPEN);
    }

    @Test
    void mapsLtpModeWithoutOptionalFields() {
        FeedTick source = new FeedTick(
                FeedMode.LTP, Exchange.NSE, "3045", 8L,
                Instant.parse("2026-11-18T04:00:03Z"),
                new java.math.BigDecimal("801.00"),
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);

        Tick tick = mapper.toDomain(source, Instant.parse("2026-11-18T04:00:04Z"), MarketStatus.OPEN);

        assertThat(tick.mode()).isEqualTo(TickMode.LTP);
        assertThat(tick.depth().isEmpty()).isTrue();
        assertThat(tick.lastTradedPrice()).isEqualByComparingTo("801.00");
    }

    @Test
    void rejectsNullTick() {
        assertThatThrownBy(() -> mapper.toDomain(null, Instant.now(), MarketStatus.CLOSED))
                .isInstanceOf(MarketDataMappingException.class);
    }
}
