package com.tradingassistant.execution.domain;

import com.tradingassistant.market.domain.InstrumentKey;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable record of where one trade stands across its order and position.
 *
 * <p>It summarises facts only: what was filled, what was closed, what was realised
 * and when. It never states whether the trade should continue, stop or reverse.</p>
 *
 * @param orderId           application order identity
 * @param positionId        application position identity
 * @param strategyId        strategy the trade belongs to
 * @param instrument        instrument traded
 * @param direction         long or short
 * @param positionStatus    lifecycle state of the position
 * @param filledQuantity    quantity filled by the order
 * @param averageEntryPrice average entry price
 * @param closedQuantity    quantity closed so far
 * @param realisedPnl       realised profit and loss
 * @param openedAt          instant the trade opened
 * @param updatedAt         instant of the last change
 * @param closedAt          instant the trade closed, when it did
 */
public record TradeLifecycle(
        String orderId,
        String positionId,
        String strategyId,
        InstrumentKey instrument,
        PositionDirection direction,
        PositionStatus positionStatus,
        BigDecimal filledQuantity,
        BigDecimal averageEntryPrice,
        BigDecimal closedQuantity,
        BigDecimal realisedPnl,
        Instant openedAt,
        Instant updatedAt,
        Optional<Instant> closedAt) {

    public TradeLifecycle {
        orderId = ExecutionValue.requireText(orderId, "orderId");
        positionId = ExecutionValue.requireText(positionId, "positionId");
        strategyId = ExecutionValue.requireText(strategyId, "strategyId");
        instrument = Objects.requireNonNull(instrument, "instrument must not be null");
        direction = Objects.requireNonNull(direction, "direction must not be null");
        positionStatus = Objects.requireNonNull(positionStatus,
                "positionStatus must not be null");
        filledQuantity = ExecutionValue.requireNonNegative(filledQuantity,
                "filledQuantity");
        averageEntryPrice = ExecutionValue.requireNonNegative(averageEntryPrice,
                "averageEntryPrice");
        closedQuantity = ExecutionValue.requireNonNegative(closedQuantity,
                "closedQuantity");
        realisedPnl = Objects.requireNonNull(realisedPnl, "realisedPnl must not be null");
        openedAt = Objects.requireNonNull(openedAt, "openedAt must not be null");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        closedAt = closedAt == null ? Optional.empty() : closedAt;
    }

    /**
     * Builds the lifecycle view of a position filled by an order.
     */
    public static TradeLifecycle of(TradeOrder order, Position position) {
        Objects.requireNonNull(order, "order must not be null");
        Objects.requireNonNull(position, "position must not be null");
        return new TradeLifecycle(order.orderId(), position.positionId(),
                position.strategyId(), position.instrument(), position.direction(),
                position.status(), order.filledQuantity(), position.averageEntryPrice(),
                position.closedQuantity(), position.realisedPnl(), position.openedAt(),
                position.updatedAt(), position.closedAt());
    }

    public boolean completed() {
        return positionStatus == PositionStatus.CLOSED;
    }
}
