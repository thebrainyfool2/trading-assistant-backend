package com.tradingassistant.pipeline;

import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.MarketDepth;
import com.tradingassistant.market.domain.MarketStatus;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.market.domain.Tick;
import com.tradingassistant.market.domain.TickMode;
import com.tradingassistant.market.time.MarketStatusResolver;
import com.tradingassistant.pipeline.calendar.MarketCalendar;
import com.tradingassistant.pipeline.calendar.MarketCalendarRegistry;
import com.tradingassistant.pipeline.calendar.WeeklyMarketCalendar;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class PipelineTestFixtures {

    public static final InstrumentKey NSE_KEY = new InstrumentKey(Segment.NSE_CASH, "3045");
    public static final InstrumentKey SECOND_KEY = new InstrumentKey(Segment.NSE_CASH, "2885");
    public static final LocalDate TRADING_DATE = LocalDate.of(2026, 11, 18);
    public static final Instant SESSION_OPEN = ist("2026-11-18T09:15:00");

    private PipelineTestFixtures() {
    }

    public static Instant ist(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(MarketStatusResolver.MARKET_ZONE).toInstant();
    }

    public static Clock clockAt(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    public static Clock clockAt(String localDateTime) {
        return clockAt(ist(localDateTime));
    }

    public static MarketCalendar nseCalendar() {
        return WeeklyMarketCalendar.weekday(
                "NSE", MarketStatusResolver.MARKET_ZONE, MarketCalendarRegistry.EQUITY_WINDOW);
    }

    public static Tick tick(InstrumentKey instrument, long sequence, String localDateTime, String price) {
        return tick(instrument, sequence, ist(localDateTime), new BigDecimal(price), 10L);
    }

    public static Tick tick(InstrumentKey instrument, long sequence, Instant timestamp, String price, long quantity) {
        return tick(instrument, sequence, timestamp, new BigDecimal(price), quantity);
    }

    public static Tick tick(
            InstrumentKey instrument,
            long sequence,
            Instant timestamp,
            BigDecimal price,
            Long quantity) {
        return new Tick(
                TickMode.SNAPSHOT,
                instrument,
                sequence,
                timestamp,
                timestamp,
                price,
                quantity,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                MarketDepth.empty(),
                null,
                null,
                null,
                null,
                MarketStatus.OPEN);
    }

    /**
     * A tick that carries no traded price, which a quote-mode update may do.
     */
    public static Tick tickWithoutPrice(InstrumentKey instrument, long sequence, Instant timestamp) {
        return new Tick(
                TickMode.QUOTE,
                instrument,
                sequence,
                timestamp,
                timestamp,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                MarketDepth.empty(),
                null,
                null,
                null,
                null,
                MarketStatus.OPEN);
    }

    public static Candle candle(
            InstrumentKey instrument,
            Interval interval,
            Instant timestamp,
            int minuteOffset,
            String close) {
        BigDecimal value = new BigDecimal(close);
        return new Candle(
                instrument,
                interval,
                timestamp.plus(Duration.ofMinutes(minuteOffset)),
                value,
                value.add(BigDecimal.ONE),
                value.subtract(BigDecimal.ONE),
                value,
                100L + minuteOffset);
    }

    public static Candle candle(int minuteIndex, String close) {
        return candle(NSE_KEY, Interval.ONE_MINUTE, SESSION_OPEN, minuteIndex, close);
    }

    /**
     * Clock whose instant can be advanced, for idle-expiry tests.
     */
    public static final class MutableClock extends Clock {

        private volatile Instant instant;
        private final ZoneId zone;

        public MutableClock(Instant instant) {
            this(instant, ZoneOffset.UTC);
        }

        public MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        public void set(Instant newInstant) {
            this.instant = newInstant;
        }

        public void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId newZone) {
            return new MutableClock(instant, newZone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
