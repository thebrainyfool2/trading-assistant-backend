package com.tradingassistant.platform.history;

import com.tradingassistant.execution.domain.ExecutionStatus;
import com.tradingassistant.execution.domain.OrderSide;
import com.tradingassistant.execution.domain.OrderStatus;
import com.tradingassistant.execution.domain.OrderType;
import com.tradingassistant.execution.domain.TimeInForce;
import com.tradingassistant.execution.domain.TradeOrder;
import com.tradingassistant.market.domain.Segment;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Durable history of one order the application submitted or attempted.
 *
 * <p>The engine remains the source of truth for live order state. This document
 * makes history survive a restart and scopes an order to the account that placed
 * it. Engine order identity is retained but is not the document identity.</p>
 */
@Document(collection = "orders")
public record OrderRecordDocument(
        @Id String id,
        @Indexed String userId,
        @Indexed String engineOrderId,
        String strategyId,
        Segment segment,
        String symbolToken,
        OrderSide side,
        OrderType type,
        BigDecimal quantity,
        BigDecimal filledQuantity,
        BigDecimal limitPrice,
        BigDecimal triggerPrice,
        BigDecimal stopPrice,
        BigDecimal targetPrice,
        TimeInForce timeInForce,
        OrderStatus status,
        BigDecimal averageFillPrice,
        ExecutionStatus executionStatus,
        String reason,
        String positionId,
        Instant createdAt,
        Instant updatedAt) {

    public OrderRecordDocument {
        userId = Objects.requireNonNull(userId, "userId must not be null");
        strategyId = Objects.requireNonNull(strategyId, "strategyId must not be null");
        segment = Objects.requireNonNull(segment, "segment must not be null");
        symbolToken = Objects.requireNonNull(symbolToken, "symbolToken must not be null");
        side = Objects.requireNonNull(side, "side must not be null");
        type = Objects.requireNonNull(type, "type must not be null");
        quantity = Objects.requireNonNull(quantity, "quantity must not be null");
        filledQuantity = Objects.requireNonNull(filledQuantity,
                "filledQuantity must not be null");
        timeInForce = Objects.requireNonNull(timeInForce,
                "timeInForce must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        executionStatus = Objects.requireNonNull(executionStatus,
                "executionStatus must not be null");
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /**
     * Builds history for a submission outcome. A rejection before the engine
     * created an order still produces a record, with a zero quantity.
     */
    public static OrderRecordDocument from(String userId, String strategyId,
                                           Segment segment, String symbolToken,
                                           OrderSide side, OrderType type,
                                           BigDecimal quantity, BigDecimal limitPrice,
                                           BigDecimal triggerPrice,
                                           BigDecimal targetPrice,
                                           TimeInForce timeInForce,
                                           ExecutionStatus executionStatus,
                                           TradeOrder order, String reason,
                                           Instant at) {
        if (order == null) {
            return new OrderRecordDocument(null, userId, null, strategyId, segment,
                    symbolToken, side, type, quantity == null ? BigDecimal.ZERO : quantity,
                    BigDecimal.ZERO, limitPrice, triggerPrice, null, targetPrice,
                    timeInForce, OrderStatus.REJECTED, null, executionStatus, reason,
                    null, at, at);
        }
        return new OrderRecordDocument(null, userId, order.orderId(),
                order.strategyId(), order.instrument().segment(),
                order.instrument().symbolToken(), order.side(), order.type(),
                order.quantity(), order.filledQuantity(),
                decimalOrNull(order.limitPrice()), decimalOrNull(order.triggerPrice()),
                decimalOrNull(order.stopPrice()), decimalOrNull(order.targetPrice()),
                order.timeInForce(), order.status(),
                decimalOrNull(order.averageFillPrice()), executionStatus,
                reason == null ? textOrNull(order.rejectionReason()) : reason, null,
                order.createdAt(), order.updatedAt());
    }

    public OrderRecordDocument withOrder(TradeOrder order, String reason, Instant at) {
        return new OrderRecordDocument(id, userId, order.orderId(), order.strategyId(),
                order.instrument().segment(), order.instrument().symbolToken(),
                order.side(), order.type(), order.quantity(), order.filledQuantity(),
                decimalOrNull(order.limitPrice()), decimalOrNull(order.triggerPrice()),
                decimalOrNull(order.stopPrice()), decimalOrNull(order.targetPrice()),
                order.timeInForce(), order.status(),
                decimalOrNull(order.averageFillPrice()), executionStatus,
                reason == null ? this.reason : reason, positionId,
                createdAt, at);
    }

    public OrderRecordDocument withPosition(String newPositionId) {
        return new OrderRecordDocument(id, userId, engineOrderId, strategyId, segment,
                symbolToken, side, type, quantity, filledQuantity, limitPrice,
                triggerPrice, stopPrice, targetPrice, timeInForce, status,
                averageFillPrice, executionStatus, reason, newPositionId, createdAt,
                updatedAt);
    }

    private static BigDecimal decimalOrNull(java.util.Optional<BigDecimal> value) {
        return value == null ? null : value.orElse(null);
    }

    private static String textOrNull(java.util.Optional<String> value) {
        return value == null ? null : value.orElse(null);
    }
}
