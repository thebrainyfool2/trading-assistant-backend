package com.tradingassistant.pipeline.event;

import com.tradingassistant.pipeline.session.IndicatorSessionKey;

import java.time.Instant;
import java.util.Objects;

/**
 * An indicator session left the active registry. Expiry is terminal: the
 * instance is no longer updated and the key can be created again later.
 *
 * @param session    immutable session key
 * @param reason     short machine-readable reason such as {@code idle} or {@code shutdown}
 * @param occurredAt application time of publication
 */
public record SessionExpiredEvent(
        IndicatorSessionKey session,
        String reason,
        Instant occurredAt) implements MarketEvent {

    public SessionExpiredEvent {
        session = Objects.requireNonNull(session, "session must not be null");
        reason = Objects.requireNonNull(reason, "reason must not be null");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    @Override
    public MarketEventType type() {
        return MarketEventType.SESSION_EXPIRED;
    }
}
