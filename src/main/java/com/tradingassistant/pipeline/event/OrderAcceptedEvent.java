package com.tradingassistant.pipeline.event;

import com.tradingassistant.execution.domain.ExecutionReport;
import com.tradingassistant.execution.domain.TradeOrder;

import java.time.Instant;
import java.util.Objects;

/**
 * The broker accepted an order that is not filled yet.
 *
 * @param order      immutable order state
 * @param report     broker report
 * @param occurredAt application time of publication
 */
public record OrderAcceptedEvent(TradeOrder order, ExecutionReport report, Instant occurredAt)
        implements MarketEvent {

    public OrderAcceptedEvent {
        order = Objects.requireNonNull(order, "order must not be null");
        report = Objects.requireNonNull(report, "report must not be null");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    @Override
    public MarketEventType type() {
        return MarketEventType.ORDER_ACCEPTED;
    }
}
