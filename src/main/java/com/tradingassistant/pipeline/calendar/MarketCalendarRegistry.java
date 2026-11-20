package com.tradingassistant.pipeline.calendar;

import com.tradingassistant.market.domain.Exchange;
import com.tradingassistant.market.time.MarketStatusResolver;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves the calendar for an exchange.
 *
 * <p>The default registration uses the regular session windows of the Indian
 * exchanges and no holiday list. Phase 13 applies configured holiday dates to
 * the weekly calendars; special sessions remain an explicit calendar operation.
 * A different implementation can be registered for any exchange without
 * touching the pipeline.</p>
 */
public final class MarketCalendarRegistry {

    public static final SessionWindow EQUITY_WINDOW = new SessionWindow(LocalTime.of(9, 15), LocalTime.of(15, 30));
    public static final SessionWindow COMMODITY_WINDOW = new SessionWindow(LocalTime.of(9, 0), LocalTime.of(23, 30));
    public static final SessionWindow CURRENCY_WINDOW = new SessionWindow(LocalTime.of(9, 0), LocalTime.of(17, 0));

    private final Map<Exchange, MarketCalendar> calendars;

    public MarketCalendarRegistry(Map<Exchange, MarketCalendar> calendars) {
        if (calendars == null || calendars.isEmpty()) {
            throw new IllegalArgumentException("At least one exchange calendar is required");
        }
        this.calendars = Map.copyOf(new LinkedHashMap<>(calendars));
    }

    /**
     * NSE and BSE equity hours, MCX commodity hours and CDS/NCDEX currency hours.
     */
    public static MarketCalendarRegistry defaults() {
        Map<Exchange, MarketCalendar> defaults = new LinkedHashMap<>();
        defaults.put(Exchange.NSE, equityCalendar("NSE"));
        defaults.put(Exchange.BSE, equityCalendar("BSE"));
        defaults.put(Exchange.MCX, commodityCalendar("MCX"));
        defaults.put(Exchange.CDS, currencyCalendar("CDS"));
        defaults.put(Exchange.NCDEX, currencyCalendar("NCDEX"));
        return new MarketCalendarRegistry(defaults);
    }

    public Optional<MarketCalendar> find(Exchange exchange) {
        if (exchange == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(calendars.get(exchange));
    }

    public MarketCalendar get(Exchange exchange) {
        return find(exchange).orElseThrow(() -> new IllegalArgumentException(
                "No market calendar is configured for exchange: " + exchange));
    }

    /**
     * Returns a registry with one calendar replaced or added.
     */
    public MarketCalendarRegistry withCalendar(Exchange exchange, MarketCalendar calendar) {
        if (exchange == null) {
            throw new IllegalArgumentException("exchange must not be null");
        }
        if (calendar == null) {
            throw new IllegalArgumentException("calendar must not be null");
        }
        Map<Exchange, MarketCalendar> merged = new LinkedHashMap<>(calendars);
        merged.put(exchange, calendar);
        return new MarketCalendarRegistry(merged);
    }

    /**
     * Replaces an exchange's holiday set. The calendar registry is immutable, so
     * configuration produces a new registry rather than mutating a running one.
     */
    public MarketCalendarRegistry withHolidays(Exchange exchange, Set<LocalDate> holidays) {
        MarketCalendar calendar = get(exchange);
        if (!(calendar instanceof WeeklyMarketCalendar weekly)) {
            throw new IllegalArgumentException(
                    "Exchange " + exchange + " does not expose a configurable weekly calendar");
        }
        return withCalendar(exchange, weekly.withHolidays(holidays));
    }

    /**
     * Parses comma-separated ISO market dates. Blank input is an empty set.
     */
    public static Set<LocalDate> parseHolidays(String csv) {
        Set<LocalDate> dates = new LinkedHashSet<>();
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        for (String entry : csv.split(",")) {
            String value = entry.trim();
            if (value.isEmpty()) {
                continue;
            }
            try {
                dates.add(LocalDate.parse(value));
            } catch (DateTimeParseException failure) {
                throw new IllegalArgumentException("Invalid market holiday date: " + value, failure);
            }
        }
        return Set.copyOf(dates);
    }

    /**
     * Immutable view of the registered calendars.
     */
    public Map<Exchange, MarketCalendar> calendars() {
        return Map.copyOf(calendars);
    }

    private static MarketCalendar equityCalendar(String code) {
        return WeeklyMarketCalendar.weekday(code, MarketStatusResolver.MARKET_ZONE, EQUITY_WINDOW);
    }

    private static MarketCalendar commodityCalendar(String code) {
        return WeeklyMarketCalendar.weekday(code, MarketStatusResolver.MARKET_ZONE, COMMODITY_WINDOW);
    }

    private static MarketCalendar currencyCalendar(String code) {
        return WeeklyMarketCalendar.weekday(code, MarketStatusResolver.MARKET_ZONE, CURRENCY_WINDOW);
    }
}
