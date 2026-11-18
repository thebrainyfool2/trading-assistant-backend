package com.tradingassistant.pipeline.calendar;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One concrete exchange trading session resolved to UTC instants.
 *
 * <p>The window is half-open: {@code open} is inclusive and {@code close} is
 * exclusive. The post-close auction window reported by
 * {@code MarketStatusResolver} is not part of the trading session, so ticks that
 * arrive during the auction are not extended into this window.</p>
 *
 * @param calendar    stable calendar code, for example {@code NSE}
 * @param tradingDate market date of the session in the calendar time zone
 * @param open        session open as a UTC instant
 * @param close       session close as a UTC instant, exclusive
 */
public record TradingSession(String calendar, LocalDate tradingDate, Instant open, Instant close) {

    private static final Duration MINIMUM_DURATION = Duration.ofMinutes(1);

    public TradingSession {
        if (calendar == null || calendar.isBlank()) {
            throw new IllegalArgumentException("calendar must not be blank");
        }
        if (tradingDate == null) {
            throw new IllegalArgumentException("tradingDate must not be null");
        }
        if (open == null || close == null) {
            throw new IllegalArgumentException("Session open and close must not be null");
        }
        if (!close.isAfter(open)) {
            throw new IllegalArgumentException("Session close must be after open");
        }
        if (Duration.between(open, close).compareTo(MINIMUM_DURATION) < 0) {
            throw new IllegalArgumentException("Session must be at least one minute long");
        }
    }

    public Duration duration() {
        return Duration.between(open, close);
    }

    public boolean contains(Instant instant) {
        return instant != null
                && !instant.isBefore(open)
                && instant.isBefore(close);
    }

    public boolean isOpenBefore(Instant instant) {
        return open.isBefore(instant);
    }
}
