package com.tradingassistant.pipeline.event;

import com.tradingassistant.execution.domain.OrderExecution;
import com.tradingassistant.execution.domain.TradeOrder;

import java.time.Instant;
import java.util.Objects;

/**
 * The broker filled part of an order.
 *
 * @param order      immutable order state
 * @param execution  fill that was applied
 * @param occurredAt application time of publication
 */
public record OrderPartiallyFilledEvent(TradeOrder order, OrderExecution execution,
                                        Instant occurredAt) implements MarketEvent {

    public OrderPartiallyFilledEvent {
        order = Objects.requireNonNull(order, "order must not be null");
        execution = Objects.requireNonNull(execution, "execution must not be null");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    @Override
    public MarketEventType type() {
        return MarketEventType.ORDER_PARTIALLY_FILLED;
    }
}
