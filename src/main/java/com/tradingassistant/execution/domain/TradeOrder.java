package com.tradingassistant.execution.domain;

import com.tradingassistant.market.domain.InstrumentKey;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable state of one order.
 *
 * <p>Every update returns a new instance; the original is never modified. The
 * record carries no broker token, no account identity and no execution
 * instruction beyond what the request already said.</p>
 *
 * @param orderId         application order identity
 * @param strategyId      strategy the order belongs to
 * @param instrument      instrument being traded
 * @param side            buy or sell
 * @param type            order type
 * @param quantity        ordered quantity
 * @param filledQuantity  cumulative filled quantity
 * @param limitPrice      limit price, when the type uses one
 * @param triggerPrice    trigger price, when the type uses one
 * @param stopPrice       protective stop carried from the risk assessment
 * @param targetPrice     optional profit boundary
 * @param timeInForce     day, immediate-or-cancel or good-till-cancelled
 * @param status          lifecycle state
 * @param averageFillPrice average fill price, empty before a fill
 * @param createdAt       creation instant
 * @param updatedAt       last transition instant
 * @param rejectionReason reason for a rejection or failure, when known
 */
public record TradeOrder(
        String orderId,
        String strategyId,
        InstrumentKey instrument,
        OrderSide side,
        OrderType type,
        BigDecimal quantity,
        BigDecimal filledQuantity,
        Optional<BigDecimal> limitPrice,
        Optional<BigDecimal> triggerPrice,
        Optional<BigDecimal> stopPrice,
        Optional<BigDecimal> targetPrice,
        TimeInForce timeInForce,
        OrderStatus status,
        Optional<BigDecimal> averageFillPrice,
        Instant createdAt,
        Instant updatedAt,
        Optional<String> rejectionReason) {

    public TradeOrder {
        orderId = ExecutionValue.requireText(orderId, "orderId");
        strategyId = ExecutionValue.requireText(strategyId, "strategyId");
        instrument = Objects.requireNonNull(instrument, "instrument must not be null");
        side = Objects.requireNonNull(side, "side must not be null");
        type = Objects.requireNonNull(type, "type must not be null");
        quantity = ExecutionValue.requirePositive(quantity, "quantity");
        filledQuantity = ExecutionValue.requireNonNegative(filledQuantity, "filledQuantity");
        if (filledQuantity.compareTo(quantity) > 0) {
            throw new IllegalArgumentException("filledQuantity must not exceed quantity");
        }
        limitPrice = limitPrice == null ? Optional.empty() : limitPrice;
        triggerPrice = triggerPrice == null ? Optional.empty() : triggerPrice;
        stopPrice = stopPrice == null ? Optional.empty() : stopPrice;
        targetPrice = targetPrice == null ? Optional.empty() : targetPrice;
        timeInForce = Objects.requireNonNull(timeInForce, "timeInForce must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        averageFillPrice = averageFillPrice == null ? Optional.empty() : averageFillPrice;
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        rejectionReason = rejectionReason == null ? Optional.empty() : rejectionReason;
    }

    /**
     * Creates the immutable order for a request.
     */
    public static TradeOrder created(OrderRequest request, String orderId, Instant at) {
        return new TradeOrder(orderId, request.strategyId(), request.instrument(),
                request.side(), request.type(), request.quantity(), BigDecimal.ZERO,
                request.limitPrice(), request.triggerPrice(), request.stopPrice(),
                request.targetPrice(), request.timeInForce(), OrderStatus.CREATED,
                Optional.empty(), at, at, Optional.empty());
    }

    /**
     * Copy with a new lifecycle state.
     */
    public TradeOrder withStatus(OrderStatus newStatus, Instant at) {
        return new TradeOrder(orderId, strategyId, instrument, side, type, quantity,
                filledQuantity, limitPrice, triggerPrice, stopPrice, targetPrice,
                timeInForce, newStatus, averageFillPrice, createdAt, at,
                newStatus == OrderStatus.REJECTED || newStatus == OrderStatus.FAILED
                        ? rejectionReason
                        : Optional.empty());
    }

    /**
     * Copy carrying a rejection or failure reason.
     */
    public TradeOrder withReason(String reason, Instant at) {
        return new TradeOrder(orderId, strategyId, instrument, side, type, quantity,
                filledQuantity, limitPrice, triggerPrice, stopPrice, targetPrice,
                timeInForce, status, averageFillPrice, createdAt, at,
                Optional.ofNullable(reason));
    }

    /**
     * Copy after a fill, keeping the cumulative filled quantity and average price.
     */
    public TradeOrder withFill(BigDecimal cumulativeQuantity, BigDecimal averagePrice,
                               OrderStatus newStatus, Instant at) {
        return new TradeOrder(orderId, strategyId, instrument, side, type, quantity,
                cumulativeQuantity, limitPrice, triggerPrice, stopPrice, targetPrice,
                timeInForce, newStatus, Optional.of(averagePrice), createdAt, at,
                Optional.empty());
    }

    public BigDecimal remainingQuantity() {
        return quantity.subtract(filledQuantity);
    }

    public boolean active() {
        return status.active();
    }
}
