package com.tradingassistant.execution.domain;

import com.tradingassistant.market.domain.InstrumentKey;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable state of one position.
 *
 * <p>A position records what was filled and what was closed. Profit and loss are
 * calculations over those fills; nothing here predicts, recommends or instructs.</p>
 *
 * @param positionId        application position identity
 * @param strategyId        strategy the position belongs to
 * @param instrument        instrument held
 * @param direction         long or short
 * @param quantity          opened quantity
 * @param closedQuantity    quantity already closed
 * @param averageEntryPrice average price of the opened quantity
 * @param stopPrice         protective stop carried from the risk assessment
 * @param targetPrice       optional profit boundary
 * @param realisedPnl       realised profit and loss in currency
 * @param lastPrice         last known price used for unrealised profit and loss
 * @param status            lifecycle state
 * @param openedAt          instant the position was opened
 * @param updatedAt         instant of the last change
 * @param closedAt          instant the position was fully closed, when it was
 */
public record Position(
        String positionId,
        String strategyId,
        InstrumentKey instrument,
        PositionDirection direction,
        BigDecimal quantity,
        BigDecimal closedQuantity,
        BigDecimal averageEntryPrice,
        Optional<BigDecimal> stopPrice,
        Optional<BigDecimal> targetPrice,
        BigDecimal realisedPnl,
        BigDecimal lastPrice,
        PositionStatus status,
        Instant openedAt,
        Instant updatedAt,
        Optional<Instant> closedAt) {

    public Position {
        positionId = ExecutionValue.requireText(positionId, "positionId");
        strategyId = ExecutionValue.requireText(strategyId, "strategyId");
        instrument = Objects.requireNonNull(instrument, "instrument must not be null");
        direction = Objects.requireNonNull(direction, "direction must not be null");
        quantity = ExecutionValue.requirePositive(quantity, "quantity");
        closedQuantity = ExecutionValue.requireNonNegative(closedQuantity, "closedQuantity");
        if (closedQuantity.compareTo(quantity) > 0) {
            throw new IllegalArgumentException("closedQuantity must not exceed quantity");
        }
        averageEntryPrice = ExecutionValue.requirePositive(averageEntryPrice,
                "averageEntryPrice");
        stopPrice = stopPrice == null ? Optional.empty() : stopPrice;
        targetPrice = targetPrice == null ? Optional.empty() : targetPrice;
        realisedPnl = Objects.requireNonNull(realisedPnl, "realisedPnl must not be null");
        lastPrice = ExecutionValue.requirePositive(lastPrice, "lastPrice");
        status = Objects.requireNonNull(status, "status must not be null");
        openedAt = Objects.requireNonNull(openedAt, "openedAt must not be null");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        closedAt = closedAt == null ? Optional.empty() : closedAt;
        if (status == PositionStatus.CLOSED && closedAt.isEmpty()) {
            throw new IllegalArgumentException("closedAt must be present for a closed position");
        }
    }

    public BigDecimal remainingQuantity() {
        return quantity.subtract(closedQuantity);
    }

    public BigDecimal notional() {
        return remainingQuantity().multiply(averageEntryPrice);
    }

    /**
     * Unrealised profit and loss at a mark price.
     */
    public BigDecimal unrealisedPnl(BigDecimal mark) {
        if (mark == null || status == PositionStatus.CLOSED) {
            return BigDecimal.ZERO;
        }
        return mark.subtract(averageEntryPrice)
                .multiply(remainingQuantity())
                .multiply(BigDecimal.valueOf(direction.signum()));
    }

    public boolean open() {
        return status != PositionStatus.CLOSED;
    }
}
