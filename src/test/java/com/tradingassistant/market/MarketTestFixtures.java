package com.tradingassistant.market;

import com.tradingassistant.broker.model.DepthLevel;
import com.tradingassistant.broker.model.Exchange;
import com.tradingassistant.broker.model.FeedMode;
import com.tradingassistant.broker.model.FeedTick;
import com.tradingassistant.broker.model.MarketDepth;
import com.tradingassistant.broker.model.Quote;
import com.tradingassistant.market.domain.Currency;
import com.tradingassistant.market.domain.Instrument;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.InstrumentType;
import com.tradingassistant.market.domain.OptionType;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.market.domain.TickMode;
import com.tradingassistant.market.time.MarketStatusResolver;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

public final class MarketTestFixtures {

    public static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");
    public static final Instant MARKET_OPEN = Instant.parse("2026-11-18T04:00:00Z");
    public static final InstrumentKey KEY = new InstrumentKey(Segment.NSE_CASH, "3045");
    public static final InstrumentKey OPTION_KEY = new InstrumentKey(Segment.NSE_FO, "12345");

    private MarketTestFixtures() {
    }

    public static MarketStatusResolver fixedMarketTime() {
        return new MarketStatusResolver(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    public static com.tradingassistant.broker.model.Instrument providerEquity() {
        return new com.tradingassistant.broker.model.Instrument(
                "3045",
                "SBIN-EQ",
                "SBIN",
                "",
                new BigDecimal("0.00"),
                new BigDecimal("0.05"),
                1L,
                "EQ",
                "NSE",
                new BigDecimal("100.00"));
    }

    public static com.tradingassistant.broker.model.Instrument providerOption() {
        return new com.tradingassistant.broker.model.Instrument(
                "12345",
                "SBIN28NOV242500CE",
                "SBIN",
                "28NOV24",
                new BigDecimal("2500.00"),
                new BigDecimal("0.05"),
                500L,
                "OPTSTK",
                "NFO",
                new BigDecimal("100.00"));
    }

    public static Instrument domainEquity() {
        return new Instrument(
                KEY,
                "SBIN-EQ",
                "SBIN-EQ",
                "SBIN",
                InstrumentType.EQUITY,
                new BigDecimal("0.05"),
                1L,
                new BigDecimal("100.00"),
                null,
                Currency.INR,
                null,
                BigDecimal.ZERO,
                null,
                null);
    }

    public static Instrument domainOption() {
        return new Instrument(
                OPTION_KEY,
                "SBIN28NOV242500CE",
                "SBIN28NOV242500CE",
                "SBIN",
                InstrumentType.OPTION,
                new BigDecimal("0.05"),
                500L,
                new BigDecimal("100.00"),
                null,
                Currency.INR,
                LocalDate.of(2024, 11, 28),
                new BigDecimal("2500.00"),
                OptionType.CALL,
                "SBIN");
    }

    public static Quote providerQuote() {
        return new Quote(
                Exchange.NSE,
                "3045",
                "SBIN-EQ",
                new BigDecimal("800.25"),
                new BigDecimal("795.00"),
                new BigDecimal("805.00"),
                new BigDecimal("790.00"),
                new BigDecimal("799.00"),
                10L,
                new BigDecimal("1.25"),
                new BigDecimal("0.16"),
                new BigDecimal("801.00"),
                123456L,
                42L,
                new BigDecimal("850.00"),
                new BigDecimal("750.00"),
                new BigDecimal("900.00"),
                new BigDecimal("600.00"),
                1000L,
                1200L,
                new MarketDepth(
                        List.of(new DepthLevel(new BigDecimal("800.20"), 5L, 2L)),
                        List.of(new DepthLevel(new BigDecimal("800.30"), 7L, 3L))));
    }

    public static com.tradingassistant.broker.model.Candle providerCandle() {
        return new com.tradingassistant.broker.model.Candle(
                OffsetDateTime.parse("2026-11-18T09:15:00+05:30"),
                new BigDecimal("800.00"),
                new BigDecimal("805.00"),
                new BigDecimal("795.00"),
                new BigDecimal("802.00"),
                1000L);
    }

    public static FeedTick providerTick() {
        return new FeedTick(
                FeedMode.SNAP_QUOTE,
                Exchange.NSE,
                "3045",
                7L,
                Instant.parse("2026-11-18T04:00:01Z"),
                new BigDecimal("800.25"),
                10L,
                new BigDecimal("801.00"),
                123456L,
                1000L,
                1200L,
                new BigDecimal("795.00"),
                new BigDecimal("805.00"),
                new BigDecimal("790.00"),
                new BigDecimal("799.00"),
                Instant.parse("2026-11-18T04:00:00Z"),
                42L,
                new MarketDepth(
                        List.of(new DepthLevel(new BigDecimal("800.20"), 5L, 2L)),
                        List.of(new DepthLevel(new BigDecimal("800.30"), 7L, 3L))),
                new BigDecimal("850.00"),
                new BigDecimal("750.00"),
                new BigDecimal("900.00"),
                new BigDecimal("600.00"));
    }

    public static TickMode tickMode() {
        return TickMode.SNAPSHOT;
    }
}
