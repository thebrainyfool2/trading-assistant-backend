package com.tradingassistant.execution.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable report returned by a broker adapter.
 *
 * <p>Adapters translate their own payloads into this record; nothing
 * broker-specific crosses the boundary.</p>
 *
 * @param brokerOrderId   broker identity of the order
 * @param status          order status reported by the broker
 * @param filledQuantity  cumulative filled quantity
 * @param averagePrice    average fill price, empty before a fill
 * @param message         broker message, empty when there is none
 * @param executions      fills contained in this report
 * @param timestamp       broker report instant
 */
public record ExecutionReport(
        String brokerOrderId,
        OrderStatus status,
        BigDecimal filledQuantity,
        Optional<BigDecimal> averagePrice,
        Optional<String> message,
        List<OrderExecution> executions,
        Instant timestamp) {

    public ExecutionReport {
        brokerOrderId = ExecutionValue.requireText(brokerOrderId, "brokerOrderId");
        status = Objects.requireNonNull(status, "status must not be null");
        filledQuantity = ExecutionValue.requireNonNegative(filledQuantity, "filledQuantity");
        averagePrice = averagePrice == null ? Optional.empty() : averagePrice;
        message = message == null ? Optional.empty() : message;
        if (executions == null || executions.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("executions must not contain null entries");
        }
        executions = List.copyOf(executions);
        timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
    }

    public boolean filled() {
        return status == OrderStatus.FILLED;
    }

    public boolean rejected() {
        return status == OrderStatus.REJECTED;
    }
}
