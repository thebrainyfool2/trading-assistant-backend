package com.tradingassistant.execution.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Compact immutable summary derived from a portfolio snapshot.
 *
 * @param openPositions   number of positions that are not fully closed
 * @param closedPositions number of positions that are fully closed
 * @param openQuantity    remaining quantity across open positions
 * @param exposure        notional exposure of open positions
 * @param availableCapital capital free for new margin
 * @param blockedMargin   margin reserved by working orders
 * @param usedMargin      margin consumed by open positions
 * @param realisedPnl     realised profit and loss
 * @param unrealisedPnl   unrealised profit and loss
 */
public record PortfolioSummary(
        int openPositions,
        int closedPositions,
        BigDecimal openQuantity,
        BigDecimal exposure,
        BigDecimal availableCapital,
        BigDecimal blockedMargin,
        BigDecimal usedMargin,
        BigDecimal realisedPnl,
        BigDecimal unrealisedPnl) {

    public PortfolioSummary {
        if (openPositions < 0 || closedPositions < 0) {
            throw new IllegalArgumentException("position counts must not be negative");
        }
        openQuantity = Objects.requireNonNull(openQuantity, "openQuantity must not be null");
        exposure = Objects.requireNonNull(exposure, "exposure must not be null");
        availableCapital = Objects.requireNonNull(availableCapital,
                "availableCapital must not be null");
        blockedMargin = Objects.requireNonNull(blockedMargin, "blockedMargin must not be null");
        usedMargin = Objects.requireNonNull(usedMargin, "usedMargin must not be null");
        realisedPnl = Objects.requireNonNull(realisedPnl, "realisedPnl must not be null");
        unrealisedPnl = Objects.requireNonNull(unrealisedPnl, "unrealisedPnl must not be null");
    }

    public static PortfolioSummary of(PortfolioSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        BigDecimal quantity = snapshot.openPositions().stream()
                .map(Position::remainingQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PortfolioSummary(snapshot.openPositionCount(),
                snapshot.closedPositionCount(), quantity, snapshot.exposure(),
                snapshot.availableCapital(), snapshot.blockedMargin(), snapshot.usedMargin(),
                snapshot.realisedPnl(), snapshot.unrealisedPnl());
    }
}
