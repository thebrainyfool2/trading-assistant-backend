package com.tradingassistant.platform.history;

import com.tradingassistant.execution.domain.Position;
import com.tradingassistant.execution.domain.PositionDirection;
import com.tradingassistant.execution.domain.PositionStatus;
import com.tradingassistant.market.domain.Segment;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Flattened position captured inside a portfolio snapshot.
 *
 * <p>Engine positions use {@code Optional} fields, which are a poor fit for a
 * stored document. The snapshot stores plain nullable values while the engine
 * model stays unchanged.</p>
 */
public record PortfolioPositionSnapshot(
        String positionId,
        String strategyId,
        Segment segment,
        String symbolToken,
        PositionDirection direction,
        BigDecimal quantity,
        BigDecimal closedQuantity,
        BigDecimal averageEntryPrice,
        BigDecimal stopPrice,
        BigDecimal targetPrice,
        BigDecimal lastPrice,
        BigDecimal realisedPnl,
        PositionStatus status,
        Instant openedAt,
        Instant updatedAt,
        Instant closedAt) {

    public static PortfolioPositionSnapshot from(Position position) {
        return new PortfolioPositionSnapshot(position.positionId(),
                position.strategyId(), position.instrument().segment(),
                position.instrument().symbolToken(), position.direction(),
                position.quantity(), position.closedQuantity(),
                position.averageEntryPrice(), position.stopPrice().orElse(null),
                position.targetPrice().orElse(null), position.lastPrice(),
                position.realisedPnl(), position.status(), position.openedAt(),
                position.updatedAt(), position.closedAt().orElse(null));
    }
}
