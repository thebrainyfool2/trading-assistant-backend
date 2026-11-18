package com.tradingassistant.pipeline.event;

import com.tradingassistant.market.domain.Tick;

import java.time.Instant;
import java.util.Objects;

/**
 * A normalized tick entered the pipeline. Published before aggregation so a
 * consumer can observe raw market updates without depending on candle
 * boundaries.
 *
 * @param tick       immutable domain tick
 * @param occurredAt application time of publication
 */
public record TickReceivedEvent(Tick tick, Instant occurredAt) implements MarketEvent {

    public TickReceivedEvent {
        tick = Objects.requireNonNull(tick, "tick must not be null");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    @Override
    public MarketEventType type() {
        return MarketEventType.TICK_RECEIVED;
    }
}
