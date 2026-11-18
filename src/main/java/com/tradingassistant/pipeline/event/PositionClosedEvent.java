package com.tradingassistant.pipeline.event;

import com.tradingassistant.execution.domain.Position;

import java.time.Instant;
import java.util.Objects;

/**
 * A position was fully closed.
 *
 * @param position   immutable position state
 * @param occurredAt application time of publication
 */
public record PositionClosedEvent(Position position, Instant occurredAt)
        implements MarketEvent {

    public PositionClosedEvent {
        position = Objects.requireNonNull(position, "position must not be null");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    @Override
    public MarketEventType type() {
        return MarketEventType.POSITION_CLOSED;
    }
}
