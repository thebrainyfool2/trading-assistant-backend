package com.tradingassistant.pipeline.event;

import com.tradingassistant.execution.domain.TradeLifecycle;

import java.time.Instant;
import java.util.Objects;

/**
 * A trade reached a terminal position state.
 *
 * @param lifecycle  immutable lifecycle summary
 * @param occurredAt application time of publication
 */
public record TradeCompletedEvent(TradeLifecycle lifecycle, Instant occurredAt)
        implements MarketEvent {

    public TradeCompletedEvent {
        lifecycle = Objects.requireNonNull(lifecycle, "lifecycle must not be null");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    @Override
    public MarketEventType type() {
        return MarketEventType.TRADE_COMPLETED;
    }
}
