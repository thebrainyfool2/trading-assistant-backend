package com.tradingassistant.intelligence.domain;

import com.tradingassistant.pipeline.calendar.TradingSession;

import java.util.Objects;
import java.util.Optional;

/**
 * Description of the exchange session context of the assessed candle.
 *
 * <p>{@code session} is the session containing the candle, or for
 * {@link SessionPhase#PRE_OPEN} the session that opens later on the same market
 * date. It is empty after the market, where no session is relevant.</p>
 *
 * @param phase   session phase
 * @param status  availability of the calendar context
 * @param session resolved session, if one applies
 * @param summary short deterministic explanation
 */
public record SessionAssessment(
        SessionPhase phase,
        AssessmentStatus status,
        Optional<TradingSession> session,
        String summary) {

    public SessionAssessment {
        phase = Objects.requireNonNull(phase, "phase must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        session = session == null ? Optional.empty() : session;
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        summary = summary.trim();
    }

    /**
     * True for the phases that fall inside a trading session.
     */
    public boolean withinSession() {
        return phase == SessionPhase.OPENING
                || phase == SessionPhase.REGULAR
                || phase == SessionPhase.CLOSING;
    }
}
