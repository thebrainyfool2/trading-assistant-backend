package com.tradingassistant.market.time;

import com.tradingassistant.market.domain.MarketStatus;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * The single application policy for Indian market time.
 *
 * <p>All domain timestamps are UTC {@link Instant}s. SmartAPI's unzoned candle
 * request times are interpreted in {@link #MARKET_ZONE}. This resolver keeps its
 * original weekday-only quote-status contract; holiday-aware sessions belong to
 * {@code pipeline.calendar.MarketCalendar} and are used by the live supervisor
 * and the market-data freshness projection.</p>
 */
public final class MarketStatusResolver {

    public static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kolkata");

    private static final LocalTime PRE_OPEN_START = LocalTime.of(9, 0);
    private static final LocalTime OPEN_START = LocalTime.of(9, 15);
    private static final LocalTime AUCTION_START = LocalTime.of(15, 30);
    private static final LocalTime AUCTION_END = LocalTime.of(15, 40);

    private final Clock clock;

    public MarketStatusResolver(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public Instant now() {
        return clock.instant();
    }

    /**
     * Resolves the session state for an instant. A null timestamp is explicitly
     * unknown. Weekends are closed. Exchange holidays are intentionally not
     * modelled here; use {@code MarketCalendar} for holiday-aware sessions.
     */
    public MarketStatus resolve(Instant timestamp) {
        if (timestamp == null) {
            return MarketStatus.UNKNOWN;
        }
        ZonedDateTime marketTime = timestamp.atZone(MARKET_ZONE);
        DayOfWeek day = marketTime.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return MarketStatus.CLOSED;
        }

        LocalTime time = marketTime.toLocalTime();
        if (time.isBefore(PRE_OPEN_START) || !time.isBefore(AUCTION_END)) {
            return MarketStatus.CLOSED;
        }
        if (time.isBefore(OPEN_START)) {
            return MarketStatus.PRE_OPEN;
        }
        if (time.isBefore(AUCTION_START)) {
            return MarketStatus.OPEN;
        }
        return MarketStatus.AUCTION;
    }

    public LocalDateTime toMarketTime(Instant instant) {
        Objects.requireNonNull(instant, "instant must not be null");
        return LocalDateTime.ofInstant(instant, MARKET_ZONE);
    }

    public Instant toInstant(LocalDateTime marketTime) {
        Objects.requireNonNull(marketTime, "marketTime must not be null");
        return marketTime.atZone(MARKET_ZONE).toInstant();
    }
}
