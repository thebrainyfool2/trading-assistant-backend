package com.tradingassistant.pipeline.event;

import com.tradingassistant.pipeline.session.IndicatorSession;
import com.tradingassistant.pipeline.session.SessionLifecycleListener;

import java.time.Clock;
import java.util.Objects;

/**
 * Bridges registry lifecycle transitions to the event model, so the registry
 * itself has no dependency on event types or publishers.
 */
public final class MarketEventSessionListener implements SessionLifecycleListener {

    private final MarketEventPublisher publisher;
    private final Clock clock;

    public MarketEventSessionListener(MarketEventPublisher publisher, Clock clock) {
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void onSessionStarted(IndicatorSession session) {
        publisher.publish(new SessionStartedEvent(
                session.key(), session.indicatorTypes(), clock.instant()));
    }

    @Override
    public void onSessionExpired(IndicatorSession session, String reason) {
        publisher.publish(new SessionExpiredEvent(session.key(), reason, clock.instant()));
    }
}
