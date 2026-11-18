package com.tradingassistant.pipeline.event;

import com.tradingassistant.execution.domain.TradeOrder;

import java.time.Instant;
import java.util.Objects;

/**
 * An order was created from a qualified, assessed trade.
 *
 * @param order      immutable order state
 * @param occurredAt application time of publication
 */
public record OrderCreatedEvent(TradeOrder order, Instant occurredAt) implements MarketEvent {

    public OrderCreatedEvent {
        order = Objects.requireNonNull(order, "order must not be null");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    @Override
    public MarketEventType type() {
        return MarketEventType.ORDER_CREATED;
    }
}
