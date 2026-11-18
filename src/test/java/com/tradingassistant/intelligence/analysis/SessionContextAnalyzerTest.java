package com.tradingassistant.intelligence.analysis;

import com.tradingassistant.intelligence.config.MarketIntelligenceConfig;
import com.tradingassistant.intelligence.domain.AnalysisContext;
import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.intelligence.domain.SessionAssessment;
import com.tradingassistant.intelligence.domain.SessionPhase;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.pipeline.calendar.MarketCalendar;
import com.tradingassistant.pipeline.calendar.MarketCalendarRegistry;
import com.tradingassistant.pipeline.calendar.SessionWindow;
import com.tradingassistant.pipeline.calendar.WeeklyMarketCalendar;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static com.tradingassistant.intelligence.IntelligenceTestFixtures.KEY;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.calendar;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.candleAt;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.ist;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.result;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.series;
import static com.tradingassistant.market.domain.Interval.ONE_MINUTE;
import static org.assertj.core.api.Assertions.assertThat;

class SessionContextAnalyzerTest {

    private final SessionContextAnalyzer analyzer = new SessionContextAnalyzer();
    private final MarketIntelligenceConfig config = MarketIntelligenceConfig.defaults();

    @Test
    void resolvesTheOpeningPhase() {
        SessionAssessment assessment = analyze(calendar(), "2026-11-18T09:20:00", config);

        assertThat(assessment.phase()).isEqualTo(SessionPhase.OPENING);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.READY);
        assertThat(assessment.withinSession()).isTrue();
        assertThat(assessment.session()).isPresent();
        assertThat(assessment.session().orElseThrow().close()).isEqualTo(ist("2026-11-18T15:30:00"));
    }

    @Test
    void resolvesTheRegularPhase() {
        SessionAssessment assessment = analyze(calendar(), "2026-11-18T10:00:00", config);

        assertThat(assessment.phase()).isEqualTo(SessionPhase.REGULAR);
        assertThat(assessment.session()).isPresent();
    }

    @Test
    void resolvesTheClosingPhase() {
        SessionAssessment assessment = analyze(calendar(), "2026-11-18T15:20:00", config);

        assertThat(assessment.phase()).isEqualTo(SessionPhase.CLOSING);
        assertThat(assessment.withinSession()).isTrue();
    }

    @Test
    void resolvesPreOpenBeforeTodaysSession() {
        SessionAssessment assessment = analyze(calendar(), "2026-11-18T08:00:00", config);

        assertThat(assessment.phase()).isEqualTo(SessionPhase.PRE_OPEN);
        assertThat(assessment.withinSession()).isFalse();
        assertThat(assessment.session()).isPresent();
        assertThat(assessment.session().orElseThrow().open()).isEqualTo(ist("2026-11-18T09:15:00"));
    }

    @Test
    void resolvesAfterMarketAndWeekends() {
        SessionAssessment afterClose = analyze(calendar(), "2026-11-18T16:00:00", config);
        SessionAssessment weekend = analyze(calendar(), "2026-11-21T10:00:00", config);

        assertThat(afterClose.phase()).isEqualTo(SessionPhase.AFTER_MARKET);
        assertThat(afterClose.session()).isEmpty();
        assertThat(weekend.phase()).isEqualTo(SessionPhase.AFTER_MARKET);
    }

    @Test
    void honoursASpecialHalfDaySession() {
        WeeklyMarketCalendar halfDay = ((WeeklyMarketCalendar) calendar())
                .withSpecialSession(LocalDate.of(2026, 11, 18),
                        new SessionWindow(LocalTime.of(9, 15), LocalTime.of(13, 0)));

        SessionAssessment assessment = analyze(halfDay, "2026-11-18T12:50:00", config);

        assertThat(assessment.phase()).isEqualTo(SessionPhase.CLOSING);
        assertThat(assessment.session().orElseThrow().close()).isEqualTo(ist("2026-11-18T13:00:00"));
    }

    @Test
    void sessionWindowsAreConfigurable() {
        MarketIntelligenceConfig longOpening = MarketIntelligenceConfig.builder()
                .sessionWindows(Duration.ofHours(1), Duration.ofMinutes(15))
                .build();

        SessionAssessment assessment = analyze(calendar(), "2026-11-18T10:00:00", longOpening);

        assertThat(assessment.phase()).isEqualTo(SessionPhase.OPENING);
    }

    private SessionAssessment analyze(
            MarketCalendar marketCalendar,
            String localDateTime,
            MarketIntelligenceConfig configuration) {
        Candle candle = candleAt(ist(localDateTime), "100");
        AnalysisContext context = new AnalysisContext(
                KEY,
                ONE_MINUTE,
                List.of(candle),
                Map.of("SMA", series("SMA", List.of(
                        result("SMA", candle.timestamp(), "value", "99")))),
                marketCalendar);
        return analyzer.analyze(context, configuration);
    }

    @Test
    void registryCalendarsAreResolvedForTheInstrumentExchange() {
        assertThat(MarketCalendarRegistry.defaults().get(
                com.tradingassistant.market.domain.Exchange.NSE).code()).isEqualTo("NSE");
    }
}
