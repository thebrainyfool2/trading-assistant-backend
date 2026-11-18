package com.tradingassistant.pipeline.calendar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Exchange calendar abstraction used by aggregation, session alignment and all
 * future market-processing engines.
 *
 * <p>The interface deliberately exposes trading <em>days</em> and <em>sessions</em>
 * rather than hardcoding weekends: trading weekdays, holidays and special
 * sessions are supplied by an implementation, so a Muhurat session, a half day
 * or a future holiday list does not require a code change. All instants are
 * UTC; {@link #zone()} is the market time zone used for date boundaries.</p>
 */
public interface MarketCalendar {

    /**
     * Stable calendar code, for example {@code NSE}. It is used in events and
     * logs, not as a broker identifier.
     */
    String code();

    /**
     * Market time zone in which trading dates and windows are evaluated.
     */
    ZoneId zone();

    /**
     * Recurring window used for ordinary sessions and for bucket alignment.
     */
    SessionWindow regularWindow();

    boolean isTradingDay(LocalDate date);

    boolean isTradingTime(Instant instant);

    /**
     * Resolves the session for one market date. Empty for weekends, holidays
     * and any date the implementation does not trade.
     */
    Optional<TradingSession> sessionOn(LocalDate date);

    /**
     * Session that contains the instant, or empty outside a session.
     */
    Optional<TradingSession> currentSession(Instant instant);

    /**
     * First session whose open is strictly after the instant. Empty only when
     * the search horizon is exhausted.
     */
    Optional<TradingSession> nextTradingSession(Instant instant);

    /**
     * Market date for an instant, resolved in {@link #zone()}.
     */
    default LocalDate marketDate(Instant instant) {
        if (instant == null) {
            throw new IllegalArgumentException("instant must not be null");
        }
        return instant.atZone(zone()).toLocalDate();
    }
}
