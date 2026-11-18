package com.tradingassistant.pipeline.calendar;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A data-driven calendar for one exchange.
 *
 * <p>Trading weekdays, holidays and special sessions are supplied as data, so
 * weekends are not hardcoded into the session logic and a future holiday feed or
 * a Muhurat session only changes the data. Precedence is:</p>
 * <ol>
 *   <li>special session for the date (a Muhurat or half-day override),</li>
 *   <li>holiday (no session, even on a trading weekday),</li>
 *   <li>regular window on a configured trading weekday.</li>
 * </ol>
 *
 * <p>{@link #nextTradingSession(Instant)} searches forward a bounded number of
 * days, so a misconfigured calendar fails deterministically instead of looping.</p>
 */
public final class WeeklyMarketCalendar implements MarketCalendar {

    public static final int DEFAULT_LOOKAHEAD_DAYS = 366;

    private static final Set<DayOfWeek> WEEKDAYS = Set.of(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY);

    private final String code;
    private final ZoneId zone;
    private final SessionWindow regularWindow;
    private final Set<DayOfWeek> tradingDays;
    private final Set<LocalDate> holidays;
    private final Map<LocalDate, SessionWindow> specialSessions;
    private final int lookaheadDays;

    public WeeklyMarketCalendar(
            String code,
            ZoneId zone,
            SessionWindow regularWindow,
            Set<DayOfWeek> tradingDays,
            Set<LocalDate> holidays,
            Map<LocalDate, SessionWindow> specialSessions) {
        this(code, zone, regularWindow, tradingDays, holidays, specialSessions, DEFAULT_LOOKAHEAD_DAYS);
    }

    public WeeklyMarketCalendar(
            String code,
            ZoneId zone,
            SessionWindow regularWindow,
            Set<DayOfWeek> tradingDays,
            Set<LocalDate> holidays,
            Map<LocalDate, SessionWindow> specialSessions,
            int lookaheadDays) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (lookaheadDays < 1) {
            throw new IllegalArgumentException("lookaheadDays must be positive");
        }
        this.code = code.trim();
        this.zone = Objects.requireNonNull(zone, "zone must not be null");
        this.regularWindow = Objects.requireNonNull(regularWindow, "regularWindow must not be null");
        this.tradingDays = tradingDays == null ? WEEKDAYS : Set.copyOf(tradingDays);
        this.holidays = holidays == null ? Set.of() : Set.copyOf(holidays);
        this.specialSessions = specialSessions == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(specialSessions));
        this.lookaheadDays = lookaheadDays;
    }

    /**
     * A Monday-to-Friday calendar with no holidays.
     */
    public static WeeklyMarketCalendar weekday(String code, ZoneId zone, SessionWindow window) {
        return new WeeklyMarketCalendar(code, zone, window, WEEKDAYS, Set.of(), Map.of());
    }

    public WeeklyMarketCalendar withHolidays(Set<LocalDate> dates) {
        return new WeeklyMarketCalendar(code, zone, regularWindow, tradingDays, dates, specialSessions, lookaheadDays);
    }

    public WeeklyMarketCalendar withSpecialSession(LocalDate date, SessionWindow window) {
        Map<LocalDate, SessionWindow> merged = new LinkedHashMap<>(specialSessions);
        merged.put(Objects.requireNonNull(date, "date must not be null"), window);
        return new WeeklyMarketCalendar(code, zone, regularWindow, tradingDays, holidays, merged, lookaheadDays);
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public ZoneId zone() {
        return zone;
    }

    @Override
    public SessionWindow regularWindow() {
        return regularWindow;
    }

    @Override
    public boolean isTradingDay(LocalDate date) {
        return sessionOn(date).isPresent();
    }

    @Override
    public boolean isTradingTime(Instant instant) {
        return currentSession(instant).isPresent();
    }

    @Override
    public Optional<TradingSession> sessionOn(LocalDate date) {
        if (date == null) {
            return Optional.empty();
        }
        SessionWindow special = specialSessions.get(date);
        if (special != null) {
            return Optional.of(special.session(code, date, zone));
        }
        if (holidays.contains(date) || !tradingDays.contains(date.getDayOfWeek())) {
            return Optional.empty();
        }
        return Optional.of(regularWindow.session(code, date, zone));
    }

    @Override
    public Optional<TradingSession> currentSession(Instant instant) {
        if (instant == null) {
            return Optional.empty();
        }
        return sessionOn(marketDate(instant)).filter(session -> session.contains(instant));
    }

    @Override
    public Optional<TradingSession> nextTradingSession(Instant instant) {
        if (instant == null) {
            return Optional.empty();
        }
        LocalDate start = marketDate(instant);
        for (int offset = 0; offset <= lookaheadDays; offset++) {
            Optional<TradingSession> candidate = sessionOn(start.plusDays(offset));
            if (candidate.isPresent() && candidate.get().open().isAfter(instant)) {
                return candidate;
            }
        }
        return Optional.empty();
    }

    /**
     * Immutable copy of the configured holidays, exposed for diagnostics.
     */
    public Set<LocalDate> holidays() {
        return Set.copyOf(new LinkedHashSet<>(holidays));
    }

    /**
     * Immutable copy of the configured special sessions, exposed for diagnostics.
     */
    public Map<LocalDate, SessionWindow> specialSessions() {
        return Map.copyOf(new LinkedHashMap<>(specialSessions));
    }
}
