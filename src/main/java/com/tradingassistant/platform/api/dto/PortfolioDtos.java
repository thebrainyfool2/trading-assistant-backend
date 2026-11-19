package com.tradingassistant.platform.api.dto;

import com.tradingassistant.execution.domain.PositionDirection;
import com.tradingassistant.execution.domain.PositionStatus;
import com.tradingassistant.platform.executionapp.PortfolioQueryService;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.platform.history.PortfolioPositionSnapshot;
import com.tradingassistant.platform.history.PortfolioSnapshotDocument;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Portfolio, position and snapshot contract.
 */
public final class PortfolioDtos {

    private PortfolioDtos() {
    }

    public record PositionView(
            String positionId,
            String strategyId,
            Segment segment,
            String symbolToken,
            PositionDirection direction,
            BigDecimal quantity,
            BigDecimal closedQuantity,
            BigDecimal remainingQuantity,
            BigDecimal averageEntryPrice,
            BigDecimal stopPrice,
            BigDecimal targetPrice,
            BigDecimal lastPrice,
            BigDecimal realisedPnl,
            PositionStatus status,
            Instant openedAt,
            Instant updatedAt,
            Instant closedAt) {

        public static PositionView from(PortfolioPositionSnapshot position) {
            BigDecimal remaining = position.quantity() == null ? BigDecimal.ZERO
                    : position.quantity().subtract(position.closedQuantity() == null
                    ? BigDecimal.ZERO : position.closedQuantity());
            return new PositionView(position.positionId(), position.strategyId(),
                    position.segment(), position.symbolToken(), position.direction(),
                    position.quantity(), position.closedQuantity(), remaining,
                    position.averageEntryPrice(), position.stopPrice(),
                    position.targetPrice(), position.lastPrice(),
                    position.realisedPnl(), position.status(), position.openedAt(),
                    position.updatedAt(), position.closedAt());
        }
    }

    public record PortfolioView(
            String source,
            Instant capturedAt,
            BigDecimal capital,
            BigDecimal availableCapital,
            BigDecimal blockedMargin,
            BigDecimal usedMargin,
            BigDecimal realisedPnl,
            BigDecimal unrealisedPnl,
            BigDecimal exposure,
            int openPositions,
            int closedPositions,
            List<PositionView> positions) {

        public static PortfolioView from(PortfolioQueryService.PortfolioView view) {
            return new PortfolioView(view.source(), view.capturedAt(), view.capital(),
                    view.availableCapital(), view.blockedMargin(), view.usedMargin(),
                    view.realisedPnl(), view.unrealisedPnl(), view.exposure(),
                    view.openPositions(), view.closedPositions(),
                    view.positions().stream().map(PositionView::from).toList());
        }
    }

    public record PortfolioSnapshotView(
            String id,
            Instant capturedAt,
            BigDecimal capital,
            BigDecimal availableCapital,
            BigDecimal blockedMargin,
            BigDecimal usedMargin,
            BigDecimal realisedPnl,
            BigDecimal unrealisedPnl,
            BigDecimal exposure,
            int openPositions,
            int closedPositions,
            List<PositionView> positions) {

        public static PortfolioSnapshotView from(PortfolioSnapshotDocument snapshot) {
            return new PortfolioSnapshotView(snapshot.id(), snapshot.capturedAt(),
                    snapshot.capital(), snapshot.availableCapital(),
                    snapshot.blockedMargin(), snapshot.usedMargin(),
                    snapshot.realisedPnl(), snapshot.unrealisedPnl(),
                    snapshot.exposure(), snapshot.openPositions(),
                    snapshot.closedPositions(), snapshot.positions().stream()
                    .map(PositionView::from).toList());
        }
    }
}
