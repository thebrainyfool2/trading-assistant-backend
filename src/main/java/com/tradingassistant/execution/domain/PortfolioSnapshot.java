package com.tradingassistant.execution.domain;

import com.tradingassistant.market.domain.InstrumentKey;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable view of the portfolio at one instant.
 *
 * <p>{@code blockedMargin} is reserved for orders that are working but not filled;
 * {@code usedMargin} is consumed by open positions. {@code availableCapital} is
 * the reference capital plus realised profit and loss minus both margins.</p>
 *
 * @param capital          reference capital of the execution policy
 * @param availableCapital capital free for new margin
 * @param blockedMargin    margin reserved by working orders
 * @param usedMargin       margin consumed by open positions
 * @param realisedPnl      realised profit and loss of closed quantity
 * @param unrealisedPnl    unrealised profit and loss of open positions
 * @param exposure         notional exposure of open positions
 * @param openPositions    positions that are not fully closed
 * @param closedPositions  positions that are fully closed
 */
public record PortfolioSnapshot(
        BigDecimal capital,
        BigDecimal availableCapital,
        BigDecimal blockedMargin,
        BigDecimal usedMargin,
        BigDecimal realisedPnl,
        BigDecimal unrealisedPnl,
        BigDecimal exposure,
        List<Position> openPositions,
        List<Position> closedPositions) {

    public PortfolioSnapshot {
        capital = Objects.requireNonNull(capital, "capital must not be null");
        availableCapital = Objects.requireNonNull(availableCapital,
                "availableCapital must not be null");
        blockedMargin = Objects.requireNonNull(blockedMargin, "blockedMargin must not be null");
        usedMargin = Objects.requireNonNull(usedMargin, "usedMargin must not be null");
        realisedPnl = Objects.requireNonNull(realisedPnl, "realisedPnl must not be null");
        unrealisedPnl = Objects.requireNonNull(unrealisedPnl, "unrealisedPnl must not be null");
        exposure = Objects.requireNonNull(exposure, "exposure must not be null");
        if (openPositions == null || openPositions.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("openPositions must not contain null entries");
        }
        if (closedPositions == null || closedPositions.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("closedPositions must not contain null entries");
        }
        openPositions = List.copyOf(openPositions);
        closedPositions = List.copyOf(closedPositions);
    }

    public static PortfolioSnapshot empty(BigDecimal capital) {
        BigDecimal zero = BigDecimal.ZERO;
        return new PortfolioSnapshot(capital, capital, zero, zero, zero, zero, zero,
                List.of(), List.of());
    }

    public int openPositionCount() {
        return openPositions.size();
    }

    public int closedPositionCount() {
        return closedPositions.size();
    }

    /**
     * The open position carrying a strategy and instrument, when one exists.
     */
    public Optional<Position> position(String strategyId, InstrumentKey instrument) {
        if (strategyId == null || instrument == null) {
            return Optional.empty();
        }
        return openPositions.stream()
                .filter(position -> position.strategyId().equals(strategyId)
                        && position.instrument().equals(instrument))
                .findFirst();
    }
}
