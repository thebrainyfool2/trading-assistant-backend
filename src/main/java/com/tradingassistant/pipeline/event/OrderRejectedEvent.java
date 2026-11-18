package com.tradingassistant.pipeline.event;

import com.tradingassistant.execution.domain.TradeOrder;

import java.time.Instant;
import java.util.Objects;

/**
 * An order was refused by validation or by the broker.
 *
 * @param order      immutable order state
 * @param reason     deterministic reason
 * @param occurredAt application time of publication
 */
public record OrderRejectedEvent(TradeOrder order, String reason, Instant occurredAt)
        implements MarketEvent {

    public OrderRejectedEvent {
        order = Objects.requireNonNull(order, "order must not be null");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    @Override
    public MarketEventType type() {
        return MarketEventType.ORDER_REJECTED;
    }
}
