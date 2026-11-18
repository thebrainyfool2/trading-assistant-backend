package com.tradingassistant.intelligence.analysis;

import com.tradingassistant.intelligence.api.MarketAnalyzer;
import com.tradingassistant.intelligence.config.MarketIntelligenceConfig;
import com.tradingassistant.intelligence.domain.AnalysisContext;
import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.intelligence.domain.SessionAssessment;
import com.tradingassistant.intelligence.domain.SessionPhase;
import com.tradingassistant.pipeline.calendar.MarketCalendar;
import com.tradingassistant.pipeline.calendar.TradingSession;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Describes where the assessed candle sits in the exchange session.
 *
 * <p>Only the calendar decides whether the market is trading: no weekday,
 * holiday or clock time is hardcoded here. The opening and closing phases are
 * configured durations measured from the resolved session boundaries, so a
 * half-day is described correctly without special cases.</p>
 *
 * <p>When the candle falls outside a session, the phase is {@code PRE_OPEN} if a
 * session opens later on the same market date, and {@code AFTER_MARKET}
 * otherwise, which includes weekends and holidays.</p>
 */
public final class SessionContextAnalyzer implements MarketAnalyzer<SessionAssessment> {

    public static final String NAME = "session";

    private static final String PRE_OPEN_SUMMARY =
            "Trading has not opened for the current market date.";
    private static final String OPENING_SUMMARY =
            "The session is within its configured opening window.";
    private static final String REGULAR_SUMMARY =
            "The session is in its regular phase.";
    private static final String CLOSING_SUMMARY =
            "The session is within its configured closing window.";
    private static final String AFTER_MARKET_SUMMARY =
            "The market is closed for the current session.";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public SessionAssessment analyze(AnalysisContext context, MarketIntelligenceConfig config) {
        Instant timestamp = context.latestCandle().timestamp();
        MarketCalendar calendar = context.calendar();

        Optional<TradingSession> current = calendar.currentSession(timestamp);
        if (current.isPresent()) {
            TradingSession session = current.get();
            Duration elapsed = Duration.between(session.open(), timestamp);
            Duration remaining = Duration.between(timestamp, session.close());
            SessionPhase phase;
            if (elapsed.compareTo(config.openingWindow()) < 0) {
                phase = SessionPhase.OPENING;
            } else if (remaining.compareTo(config.closingWindow()) <= 0) {
                phase = SessionPhase.CLOSING;
            } else {
                phase = SessionPhase.REGULAR;
            }
            return new SessionAssessment(
                    phase, AssessmentStatus.READY, current, summaryFor(phase));
        }

        Optional<TradingSession> next = calendar.nextTradingSession(timestamp);
        boolean opensLaterToday = next
                .map(session -> session.tradingDate().equals(calendar.marketDate(timestamp)))
                .orElse(false);
        if (opensLaterToday) {
            return new SessionAssessment(
                    SessionPhase.PRE_OPEN, AssessmentStatus.READY, next, PRE_OPEN_SUMMARY);
        }
        return new SessionAssessment(
                SessionPhase.AFTER_MARKET, AssessmentStatus.READY, Optional.empty(), AFTER_MARKET_SUMMARY);
    }

    private static String summaryFor(SessionPhase phase) {
        return switch (phase) {
            case PRE_OPEN -> PRE_OPEN_SUMMARY;
            case OPENING -> OPENING_SUMMARY;
            case REGULAR -> REGULAR_SUMMARY;
            case CLOSING -> CLOSING_SUMMARY;
            case AFTER_MARKET -> AFTER_MARKET_SUMMARY;
            case UNKNOWN -> "The session phase could not be resolved.";
        };
    }
}
