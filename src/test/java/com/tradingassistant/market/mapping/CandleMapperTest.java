package com.tradingassistant.market.mapping;

import com.tradingassistant.market.MarketTestFixtures;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.exception.MarketDataMappingException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CandleMapperTest {

    private final CandleMapper mapper = new CandleMapper();

    @Test
    void convertsOffsetTimestampToUtcInstant() {
        Candle candle = mapper.toDomain(
                MarketTestFixtures.providerCandle(),
                MarketTestFixtures.KEY,
                Interval.ONE_DAY);

        assertThat(candle.timestamp()).isEqualTo(Instant.parse("2026-11-18T03:45:00Z"));
        assertThat(candle.instrument()).isEqualTo(MarketTestFixtures.KEY);
        assertThat(candle.interval()).isEqualTo(Interval.ONE_DAY);
        assertThat(candle.close()).isEqualByComparingTo("802.00");
    }

    @Test
    void rejectsMalformedOhlc() {
        com.tradingassistant.broker.model.Candle malformed =
                new com.tradingassistant.broker.model.Candle(
                        OffsetDateTime.parse("2026-11-18T09:15:00+05:30"),
                        new BigDecimal("100"), new BigDecimal("90"),
                        new BigDecimal("95"), new BigDecimal("105"), 1L);

        assertThatThrownBy(() -> mapper.toDomain(malformed, MarketTestFixtures.KEY, Interval.ONE_DAY))
                .isInstanceOf(MarketDataMappingException.class)
                .hasMessageContaining("high");
    }
}
