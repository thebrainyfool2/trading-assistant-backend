package com.tradingassistant.pipeline.event;

import com.tradingassistant.execution.domain.Position;

import java.time.Instant;
import java.util.Objects;

/**
 * A fill changed an existing position.
 *
 * @param position   immutable position state
 * @param occurredAt application time of publication
 */
public record PositionModifiedEvent(Position position, Instant occurredAt)
        implements MarketEvent {

    public PositionModifiedEvent {
        position = Objects.requireNonNull(position, "position must not be null");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    @Override
    public MarketEventType type() {
        return MarketEventType.POSITION_MODIFIED;
    }
}
