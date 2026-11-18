package com.tradingassistant.execution.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * One immutable fill reported by a broker.
 *
 * @param executionId broker identity of the fill
 * @param orderId     application order identity
 * @param quantity    filled quantity
 * @param price       fill price
 * @param timestamp   fill instant
 */
public record OrderExecution(
        String executionId,
        String orderId,
        BigDecimal quantity,
        BigDecimal price,
        Instant timestamp) {

    public OrderExecution {
        executionId = ExecutionValue.requireText(executionId, "executionId");
        orderId = ExecutionValue.requireText(orderId, "orderId");
        quantity = ExecutionValue.requirePositive(quantity, "quantity");
        price = ExecutionValue.requirePositive(price, "price");
        timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
    }

    public BigDecimal notional() {
        return quantity.multiply(price);
    }
}
