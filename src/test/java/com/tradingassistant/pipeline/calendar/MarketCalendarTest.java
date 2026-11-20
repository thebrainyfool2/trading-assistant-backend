package com.tradingassistant.pipeline.calendar;

import com.tradingassistant.market.domain.Exchange;
import com.tradingassistant.market.time.MarketStatusResolver;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketCalendarTest {

    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 11, 18);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 11, 21);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 11, 22);
    private static final LocalDate FRIDAY = LocalDate.of(2026, 11, 20);
    private static final ZoneId ZONE = MarketStatusResolver.MARKET_ZONE;

    @Test
    void resolvesAWeekdaySessionWithHalfOpenWindow() {
        MarketCalendar calendar = nse();

        TradingSession session = calendar.sessionOn(WEDNESDAY).orElseThrow();
        assertThat(session.calendar()).isEqualTo("NSE");
        assertThat(session.tradingDate()).isEqualTo(WEDNESDAY);
        assertThat(session.open()).isEqualTo(ist("2026-11-18T09:15:00"));
        assertThat(session.close()).isEqualTo(ist("2026-11-18T15:30:00"));
        assertThat(session.duration()).isEqualTo(Duration.ofMinutes(375));

        assertThat(session.contains(ist("2026-11-18T09:14:59"))).isFalse();
        assertThat(session.contains(ist("2026-11-18T09:15:00"))).isTrue();
        assertThat(session.contains(ist("2026-11-18T15:29:59"))).isTrue();
        assertThat(session.contains(ist("2026-11-18T15:30:00"))).isFalse();
    }

    @Test
    void weekendsHaveNoSessionWithoutHardcodingInSessionLogic() {
        MarketCalendar calendar = nse();

        assertThat(calendar.isTradingDay(SATURDAY)).isFalse();
        assertThat(calendar.isTradingDay(SUNDAY)).isFalse();
        assertThat(calendar.sessionOn(SATURDAY)).isEmpty();
        assertThat(calendar.currentSession(ist("2026-11-21T10:00:00"))).isEmpty();
        assertThat(calendar.sessionOn(null)).isEmpty();
        assertThat(calendar.sessionOn(WEDNESDAY)).isPresent();
    }

    @Test
    void holidayDisablesAnOtherwiseTradingDay() {
        MarketCalendar calendar = nse().withHolidays(Set.of(WEDNESDAY));

        assertThat(calendar.isTradingDay(WEDNESDAY)).isFalse();
        assertThat(calendar.currentSession(ist("2026-11-18T10:00:00"))).isEmpty();
        assertThat(((WeeklyMarketCalendar) calendar).holidays()).containsExactly(WEDNESDAY);
    }

    @Test
    void specialSessionOverridesHolidayAndWeekend() {
        MarketCalendar muhurat = nse()
                .withHolidays(Set.of(SUNDAY))
                .withSpecialSession(SUNDAY, new SessionWindow(LocalTime.of(18, 15), LocalTime.of(19, 15)));

        TradingSession session = muhurat.sessionOn(SUNDAY).orElseThrow();
        assertThat(session.open()).isEqualTo(ist("2026-11-22T18:15:00"));
        assertThat(session.close()).isEqualTo(ist("2026-11-22T19:15:00"));
        assertThat(muhurat.isTradingTime(ist("2026-11-22T18:30:00"))).isTrue();
        assertThat(muhurat.currentSession(ist("2026-11-22T09:15:00"))).isEmpty();
    }

    @Test
    void currentSessionIsOnlyPresentInsideTheWindow() {
        MarketCalendar calendar = nse();

        assertThat(calendar.currentSession(ist("2026-11-18T09:00:00"))).isEmpty();
        assertThat(calendar.currentSession(ist("2026-11-18T09:15:00"))).isPresent();
        assertThat(calendar.currentSession(ist("2026-11-18T15:30:00"))).isEmpty();
    }

    @Test
    void nextTradingSessionSkipsWeekendAndHolidays() {
        WeeklyMarketCalendar calendar = nse();
        TradingSession monday = calendar.nextTradingSession(ist("2026-11-20T16:00:00")).orElseThrow();

        assertThat(monday.tradingDate()).isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(monday.open()).isEqualTo(ist("2026-11-23T09:15:00"));

        MarketCalendar withMondayHoliday = calendar.withHolidays(Set.of(LocalDate.of(2026, 11, 23)));
        TradingSession tuesday = withMondayHoliday
                .nextTradingSession(ist("2026-11-20T16:00:00"))
                .orElseThrow();
        assertThat(tuesday.tradingDate()).isEqualTo(LocalDate.of(2026, 11, 24));
    }

    @Test
    void nextTradingSessionFromBeforeOpenReturnsTheSameDay() {
        MarketCalendar calendar = nse();

        TradingSession today = calendar.nextTradingSession(ist("2026-11-18T08:00:00")).orElseThrow();
        assertThat(today.open()).isEqualTo(ist("2026-11-18T09:15:00"));

        TradingSession tomorrow = calendar.nextTradingSession(ist("2026-11-18T12:00:00")).orElseThrow();
        assertThat(tomorrow.tradingDate()).isEqualTo(LocalDate.of(2026, 11, 19));
    }

    @Test
    void boundedLookaheadEndsTheSearchDeterministically() {
        WeeklyMarketCalendar closed = new WeeklyMarketCalendar(
                "CLOSED", ZONE, MarketCalendarRegistry.EQUITY_WINDOW, Set.of(), Set.of(), java.util.Map.of(), 3);

        assertThat(closed.nextTradingSession(ist("2026-11-18T12:00:00"))).isEmpty();
    }

    @Test
    void marketDateUsesTheMarketZoneRatherThanUtc() {
        MarketCalendar calendar = nse();

        assertThat(calendar.marketDate(java.time.Instant.parse("2026-11-18T19:00:00Z")))
                .isEqualTo(LocalDate.of(2026, 11, 19));
        assertThat(calendar.marketDate(java.time.Instant.parse("2026-11-18T04:00:00Z")))
                .isEqualTo(WEDNESDAY);
    }

    @Test
    void registryProvidesIndianExchangeDefaults() {
        MarketCalendarRegistry registry = MarketCalendarRegistry.defaults();

        assertThat(registry.calendars()).hasSize(5);
        assertThat(registry.get(Exchange.NSE).sessionOn(WEDNESDAY).orElseThrow().close())
                .isEqualTo(ist("2026-11-18T15:30:00"));
        assertThat(registry.get(Exchange.MCX).sessionOn(WEDNESDAY).orElseThrow().close())
                .isEqualTo(ist("2026-11-18T23:30:00"));
        assertThat(registry.get(Exchange.CDS).sessionOn(WEDNESDAY).orElseThrow().open())
                .isEqualTo(ist("2026-11-18T09:00:00"));
        assertThat(registry.find(null)).isEmpty();
        assertThatThrownBy(() -> registry.get(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registryCanApplyConfiguredHolidaysWithoutMutatingOtherExchanges() {
        MarketCalendarRegistry registry = MarketCalendarRegistry.defaults()
                .withHolidays(Exchange.NSE, Set.of(WEDNESDAY));

        assertThat(registry.get(Exchange.NSE).isTradingDay(WEDNESDAY)).isFalse();
        assertThat(registry.get(Exchange.BSE).isTradingDay(WEDNESDAY)).isTrue();
        assertThat(MarketCalendarRegistry.parseHolidays(" 2026-11-18, 2026-11-19 ,"))
                .containsExactlyInAnyOrder(WEDNESDAY, LocalDate.of(2026, 11, 19));
        assertThat(MarketCalendarRegistry.parseHolidays("")).isEmpty();
        assertThatThrownBy(() -> MarketCalendarRegistry.parseHolidays("18-11-2026"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("18-11-2026");
    }

    @Test
    void aCustomCalendarCanReplaceTheDefault() {
        MarketCalendar custom = WeeklyMarketCalendar.weekday(
                "CUSTOM", ZONE, new SessionWindow(LocalTime.of(10, 0), LocalTime.of(14, 0)));
        MarketCalendarRegistry registry = MarketCalendarRegistry.defaults()
                .withCalendar(Exchange.NSE, custom);

        assertThat(registry.get(Exchange.NSE).code()).isEqualTo("CUSTOM");
        assertThat(registry.get(Exchange.NSE).sessionOn(FRIDAY).orElseThrow().open())
                .isEqualTo(ist("2026-11-20T10:00:00"));
    }

    @Test
    void invalidWindowsAndSessionsAreRejected() {
        assertThatThrownBy(() -> new SessionWindow(LocalTime.of(15, 0), LocalTime.of(10, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TradingSession("NSE", WEDNESDAY,
                ist("2026-11-18T15:30:00"), ist("2026-11-18T09:15:00")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TradingSession(" ", WEDNESDAY,
                ist("2026-11-18T09:15:00"), ist("2026-11-18T15:30:00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static WeeklyMarketCalendar nse() {
        return (WeeklyMarketCalendar) MarketCalendarRegistry.defaults().get(Exchange.NSE);
    }

    private static java.time.Instant ist(String localDateTime) {
        return java.time.LocalDateTime.parse(localDateTime).atZone(ZONE).toInstant();
    }
}
