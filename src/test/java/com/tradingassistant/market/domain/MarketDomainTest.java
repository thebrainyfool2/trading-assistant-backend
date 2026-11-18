package com.tradingassistant.market.domain;

import com.tradingassistant.market.MarketTestFixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketDomainTest {

    @Test
    void instrumentDefaultsDisplayNameAndCurrencyAndExposesKey() {
        Instrument instrument = MarketTestFixtures.domainEquity();

        assertThat(instrument.exchange()).isEqualTo(Exchange.NSE);
        assertThat(instrument.segment()).isEqualTo(Segment.NSE_CASH);
        assertThat(instrument.symbolToken()).isEqualTo("3045");
        assertThat(instrument.currency()).isEqualTo(Currency.INR);
        assertThat(instrument.isOption()).isFalse();
    }

    @Test
    void instrumentRejectsInvalidNumbersAndOptionTypeOnEquity() {
        assertThatThrownBy(() -> new Instrument(
                MarketTestFixtures.KEY, "S", "S", "S", InstrumentType.EQUITY,
                new BigDecimal("-1"), 1L, null, null, Currency.INR,
                null, null, OptionType.CALL, null))
                .hasMessageContaining("tickSize");
        assertThatThrownBy(() -> new Instrument(
                MarketTestFixtures.KEY, "S", "S", "S", InstrumentType.EQUITY,
                BigDecimal.ONE, -1L, null, null, Currency.INR,
                null, null, null, null))
                .hasMessageContaining("lotSize");
        assertThatThrownBy(() -> new Instrument(
                MarketTestFixtures.KEY, "S", "S", "S", InstrumentType.EQUITY,
                BigDecimal.ONE, 1L, null, null, Currency.INR,
                null, null, OptionType.CALL, null))
                .hasMessageContaining("optionType");
    }

    @Test
    void quoteNormalizesNullDepthAndRejectsNegativePrices() {
        Quote quote = new Quote(
                MarketTestFixtures.KEY, "SBIN-EQ", null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                Instant.parse("2026-11-18T04:00:00Z"), MarketStatus.OPEN,
                null, null, null, null, null);

        assertThat(quote.depth()).isEqualTo(MarketDepth.empty());

        assertThatThrownBy(() -> new Quote(
                MarketTestFixtures.KEY, "SBIN-EQ", new BigDecimal("-1"), null, null, null, null,
                null, null, null, null, null, null, null, null,
                Instant.parse("2026-11-18T04:00:00Z"), MarketStatus.OPEN,
                null, null, null, null, null))
                .hasMessageContaining("lastTradedPrice");
    }

    @Test
    void candleRequiresValidOhlcAndUsesInstant() {
        Candle candle = new Candle(
                MarketTestFixtures.KEY,
                Interval.ONE_DAY,
                Instant.parse("2026-11-18T04:00:00Z"),
                new BigDecimal("100"),
                new BigDecimal("110"),
                new BigDecimal("90"),
                new BigDecimal("105"),
                10L);

        assertThat(candle.timestamp()).isEqualTo(Instant.parse("2026-11-18T04:00:00Z"));
        assertThatThrownBy(() -> new Candle(
                MarketTestFixtures.KEY, Interval.ONE_DAY, Instant.now(),
                new BigDecimal("100"), new BigDecimal("90"), new BigDecimal("95"),
                new BigDecimal("105"), 1L))
                .hasMessageContaining("high");
    }

    @Test
    void depthRejectsNullLevelsAndNegativeValues() {
        assertThatThrownBy(() -> new MarketDepth(
                Collections.singletonList(null), List.of()))
                .hasMessageContaining("null");
        assertThatThrownBy(() -> new DepthLevel(new BigDecimal("-1"), 1L, 1L))
                .hasMessageContaining("price");
        assertThatThrownBy(() -> new DepthLevel(BigDecimal.ONE, -1L, 1L))
                .hasMessageContaining("quantity");
    }

    @Test
    void ltpTickRequiresPriceAndNormalizesDepth() {
        Tick tick = new Tick(
                TickMode.LTP, MarketTestFixtures.KEY, 1L,
                Instant.parse("2026-11-18T04:00:00Z"), Instant.parse("2026-11-18T04:00:01Z"),
                new BigDecimal("100"), null, null, null, null, null,
                null, null, null, null, null, null, null,
                null, null, null, null, MarketStatus.OPEN);

        assertThat(tick.depth()).isEqualTo(MarketDepth.empty());
        assertThatThrownBy(() -> new Tick(
                TickMode.LTP, MarketTestFixtures.KEY, 1L,
                Instant.parse("2026-11-18T04:00:00Z"), Instant.parse("2026-11-18T04:00:01Z"),
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, MarketStatus.OPEN))
                .hasMessageContaining("lastTradedPrice");
    }

    @Test
    void optionContractValidatesInstrumentConsistencyAndExposesSizes() {
        OptionContract contract = new OptionContract(
                MarketTestFixtures.domainOption(), "SBIN",
                MarketTestFixtures.domainOption().expiry(),
                new BigDecimal("2500.00"), OptionType.CALL);

        assertThat(contract.lotSize()).isEqualTo(500L);
        assertThat(contract.tickSize()).isEqualByComparingTo("0.05");
        assertThat(contract.exchange()).isEqualTo(Exchange.NSE);

        assertThatThrownBy(() -> new OptionContract(
                MarketTestFixtures.domainEquity(), "SBIN", null,
                BigDecimal.ONE, OptionType.CALL))
                .hasMessageContaining("OPTION");
    }
}
