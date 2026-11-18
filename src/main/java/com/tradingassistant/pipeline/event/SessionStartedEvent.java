package com.tradingassistant.pipeline.event;

import com.tradingassistant.pipeline.session.IndicatorSessionKey;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * An indicator session was created for an instrument, interval and
 * configuration.
 *
 * @param session        immutable session key
 * @param indicatorTypes indicator types the session owns
 * @param occurredAt     application time of publication
 */
public record SessionStartedEvent(
        IndicatorSessionKey session,
        Set<String> indicatorTypes,
        Instant occurredAt) implements MarketEvent {

    public SessionStartedEvent {
        session = Objects.requireNonNull(session, "session must not be null");
        indicatorTypes = indicatorTypes == null ? Set.of() : Set.copyOf(indicatorTypes);
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    @Override
    public MarketEventType type() {
        return MarketEventType.SESSION_STARTED;
    }
}
