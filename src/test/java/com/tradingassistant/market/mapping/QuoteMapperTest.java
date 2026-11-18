package com.tradingassistant.market.mapping;

import com.tradingassistant.market.MarketTestFixtures;
import com.tradingassistant.market.domain.MarketStatus;
import com.tradingassistant.market.domain.Quote;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.market.exception.MarketDataMappingException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuoteMapperTest {

    private final QuoteMapper mapper = new QuoteMapper(new MarketDepthMapper());

    @Test
    void mapsFullQuoteAndUsesReceiptTime() {
        Instant receivedAt = Instant.parse("2026-11-18T04:00:00Z");
        Quote quote = mapper.toDomain(
                MarketTestFixtures.providerQuote(), receivedAt, MarketStatus.OPEN);

        assertThat(quote.instrument().segment()).isEqualTo(Segment.NSE_CASH);
        assertThat(quote.instrument().symbolToken()).isEqualTo("3045");
        assertThat(quote.lastTradedPrice()).isEqualByComparingTo("800.25");
        assertThat(quote.previousClose()).isEqualByComparingTo("799.00");
        assertThat(quote.timestamp()).isEqualTo(receivedAt);
        assertThat(quote.marketStatus()).isEqualTo(MarketStatus.OPEN);
        assertThat(quote.depth().buy()).hasSize(1);
        assertThat(quote.depth().sell().getFirst().orders()).isEqualTo(3L);
    }

    @Test
    void rejectsNullQuote() {
        assertThatThrownBy(() -> mapper.toDomain(null, Instant.now(), MarketStatus.CLOSED))
                .isInstanceOf(MarketDataMappingException.class);
    }
}
