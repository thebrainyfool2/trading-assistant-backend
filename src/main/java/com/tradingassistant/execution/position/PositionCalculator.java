package com.tradingassistant.execution.position;

import com.tradingassistant.execution.domain.OrderSide;
import com.tradingassistant.execution.domain.Position;
import com.tradingassistant.execution.domain.PositionDirection;
import com.tradingassistant.execution.domain.PositionStatus;
import com.tradingassistant.execution.domain.TradeOrder;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure position arithmetic: open, add and close.
 *
 * <p>Every method returns a new immutable position; nothing is mutated and no
 * state is read from anywhere else. Realised profit and loss is computed from the
 * average entry price and the close price, signed by the direction.</p>
 */
public final class PositionCalculator {

    private PositionCalculator() {
    }

    /**
     * Opens a new position from a fill.
     */
    public static Position open(String positionId, TradeOrder order, BigDecimal quantity,
                                BigDecimal price, Instant at) {
        Objects.requireNonNull(order, "order must not be null");
        return new Position(positionId, order.strategyId(), order.instrument(),
                PositionDirection.of(order.side()), quantity, BigDecimal.ZERO, price,
                order.stopPrice(), order.targetPrice(), BigDecimal.ZERO, price,
                PositionStatus.OPEN, at, at, Optional.empty());
    }

    /**
     * Adds quantity to an existing position at a new average entry price.
     */
    public static Position add(Position position, BigDecimal quantity, BigDecimal price,
                               Instant at) {
        Objects.requireNonNull(position, "position must not be null");
        BigDecimal newQuantity = position.quantity().add(quantity);
        BigDecimal weighted = position.averageEntryPrice().multiply(position.quantity())
                .add(price.multiply(quantity));
        BigDecimal average = weighted.divide(newQuantity, MathContext.DECIMAL128);
        return new Position(position.positionId(), position.strategyId(),
                position.instrument(), position.direction(), newQuantity,
                position.closedQuantity(), average, position.stopPrice(),
                position.targetPrice(), position.realisedPnl(), price,
                position.status(), position.openedAt(), at, position.closedAt());
    }

    /**
     * Closes quantity at a price, realising profit and loss.
     */
    public static CloseResult close(Position position, BigDecimal quantity, BigDecimal price,
                                    Instant at) {
        Objects.requireNonNull(position, "position must not be null");
        BigDecimal closeable = quantity.min(position.remainingQuantity());
        BigDecimal delta = price.subtract(position.averageEntryPrice())
                .multiply(closeable)
                .multiply(BigDecimal.valueOf(position.direction().signum()));
        BigDecimal closed = position.closedQuantity().add(closeable);
        BigDecimal realised = position.realisedPnl().add(delta);
        boolean fullyClosed = closed.compareTo(position.quantity()) == 0;
        PositionStatus status = fullyClosed ? PositionStatus.CLOSED
                : PositionStatus.PARTIALLY_CLOSED;
        Position updated = new Position(position.positionId(), position.strategyId(),
                position.instrument(), position.direction(), position.quantity(), closed,
                position.averageEntryPrice(), position.stopPrice(), position.targetPrice(),
                realised, price, status, position.openedAt(), at,
                fullyClosed ? Optional.of(at) : Optional.empty());
        return new CloseResult(updated, delta);
    }

    /**
     * Direction implied by an order side.
     */
    public static PositionDirection direction(OrderSide side) {
        return PositionDirection.of(side);
    }

    /**
     * Result of a close: the updated position and the realised delta.
     */
    public record CloseResult(Position position, BigDecimal realisedPnl) {

        public CloseResult {
            position = Objects.requireNonNull(position, "position must not be null");
            realisedPnl = Objects.requireNonNull(realisedPnl, "realisedPnl must not be null");
        }
    }
}
