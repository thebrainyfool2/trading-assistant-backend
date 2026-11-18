package com.tradingassistant.execution.portfolio;

import com.tradingassistant.execution.domain.PortfolioSnapshot;
import com.tradingassistant.execution.domain.Position;
import com.tradingassistant.execution.domain.PositionStatus;

import java.util.Objects;

/**
 * Immutable description of what one fill did to the portfolio.
 *
 * @param position position that was affected
 * @param status   resulting position status
 * @param opened   whether the fill created the position
 * @param modified whether the fill changed an existing position
 * @param closed   whether the fill closed the position
 * @param snapshot portfolio snapshot after the change
 */
public record PortfolioChange(
        Position position,
        PositionStatus status,
        boolean opened,
        boolean modified,
        boolean closed,
        PortfolioSnapshot snapshot) {

    public PortfolioChange {
        position = Objects.requireNonNull(position, "position must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        snapshot = Objects.requireNonNull(snapshot, "snapshot must not be null");
    }
}
