package com.tradingassistant.execution.portfolio;

import com.tradingassistant.execution.config.ExecutionConfig;
import com.tradingassistant.execution.domain.OrderExecution;
import com.tradingassistant.execution.domain.OrderSide;
import com.tradingassistant.execution.domain.PortfolioSnapshot;
import com.tradingassistant.execution.domain.PortfolioSummary;
import com.tradingassistant.execution.domain.Position;
import com.tradingassistant.execution.domain.PositionDirection;
import com.tradingassistant.execution.domain.PositionStatus;
import com.tradingassistant.execution.domain.TradeOrder;
import com.tradingassistant.execution.position.PositionCalculator;
import com.tradingassistant.market.domain.InstrumentKey;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Thread-safe in-memory portfolio: open and closed positions, margins and profit
 * and loss.
 *
 * <p>The service is the single writer of portfolio state. Every public method
 * synchronizes on the instance, so a fill, a mark and a snapshot never interleave.
 * All returned values are immutable snapshots; callers cannot mutate state through
 * them. Nothing is persisted and no account is synchronized.</p>
 */
public final class PortfolioService {

    private final ExecutionConfig config;
    private final Map<String, Position> positions = new LinkedHashMap<>();
    private final Map<String, BigDecimal> blocked = new LinkedHashMap<>();
    private final Map<InstrumentKey, BigDecimal> marks = new LinkedHashMap<>();
    private BigDecimal realisedPnl = BigDecimal.ZERO;

    public PortfolioService(ExecutionConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    /**
     * Immutable snapshot of the current state.
     */
    public synchronized PortfolioSnapshot snapshot() {
        List<Position> open = new ArrayList<>();
        List<Position> closed = new ArrayList<>();
        BigDecimal usedMargin = BigDecimal.ZERO;
        BigDecimal exposure = BigDecimal.ZERO;
        BigDecimal unrealised = BigDecimal.ZERO;
        for (Position position : positions.values()) {
            if (position.status() == PositionStatus.CLOSED) {
                closed.add(position);
                continue;
            }
            open.add(position);
            BigDecimal notional = position.notional();
            exposure = exposure.add(notional);
            usedMargin = usedMargin.add(config.marginFor(notional));
            unrealised = unrealised.add(position.unrealisedPnl(markOf(position)));
        }
        BigDecimal blockedTotal = blocked.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal available = config.referenceCapital().add(realisedPnl)
                .subtract(usedMargin).subtract(blockedTotal);
        return new PortfolioSnapshot(config.referenceCapital(), available, blockedTotal,
                usedMargin, realisedPnl, unrealised, exposure, open, closed);
    }

    public synchronized PortfolioSummary summary() {
        return PortfolioSummary.of(snapshot());
    }

    /**
     * The open position carrying a strategy and instrument, when one exists.
     */
    public synchronized Optional<Position> position(String strategyId,
                                                    InstrumentKey instrument) {
        if (strategyId == null || instrument == null) {
            return Optional.empty();
        }
        return positions.values().stream()
                .filter(position -> position.status() != PositionStatus.CLOSED)
                .filter(position -> position.strategyId().equals(strategyId)
                        && position.instrument().equals(instrument))
                .findFirst();
    }

    public synchronized List<Position> openPositions() {
        return snapshot().openPositions();
    }

    public synchronized int openPositionCount() {
        return snapshot().openPositionCount();
    }

    /**
     * Reserves margin for a working order.
     */
    public synchronized PortfolioSnapshot block(String orderId, BigDecimal margin) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        if (margin != null && margin.signum() > 0) {
            blocked.put(orderId, margin);
        }
        return snapshot();
    }

    /**
     * Releases margin reserved for an order that will not fill.
     */
    public synchronized PortfolioSnapshot release(String orderId) {
        if (orderId != null) {
            blocked.remove(orderId);
        }
        return snapshot();
    }

    public synchronized Optional<BigDecimal> blockedMargin(String orderId) {
        return orderId == null ? Optional.empty()
                : Optional.ofNullable(blocked.get(orderId));
    }

    /**
     * Applies a fill: opens, adds to or closes the position and realises profit and
     * loss when quantity is closed.
     */
    public synchronized PortfolioChange applyFill(TradeOrder order,
                                                  OrderExecution execution) {
        Objects.requireNonNull(order, "order must not be null");
        Objects.requireNonNull(execution, "execution must not be null");
        blocked.remove(order.orderId());
        Optional<Position> existing = position(order.strategyId(), order.instrument());
        PortfolioChange change;
        if (existing.isEmpty()) {
            Position opened = PositionCalculator.open(positionId(order), order,
                    execution.quantity(), execution.price(), execution.timestamp());
            positions.put(opened.positionId(), opened);
            change = new PortfolioChange(opened, opened.status(), true, false, false,
                    snapshot());
        } else {
            Position current = existing.get();
            boolean sameDirection =
                    current.direction() == PositionDirection.of(order.side());
            if (sameDirection) {
                Position updated = PositionCalculator.add(current, execution.quantity(),
                        execution.price(), execution.timestamp());
                positions.put(updated.positionId(), updated);
                change = new PortfolioChange(updated, updated.status(), false, true, false,
                        snapshot());
            } else {
                PositionCalculator.CloseResult result = PositionCalculator.close(current,
                        execution.quantity(), execution.price(), execution.timestamp());
                positions.put(result.position().positionId(), result.position());
                realisedPnl = realisedPnl.add(result.realisedPnl());
                change = new PortfolioChange(result.position(), result.position().status(),
                        false, true,
                        result.position().status() == PositionStatus.CLOSED, snapshot());
            }
        }
        return change;
    }

    /**
     * Records the latest known price for an instrument so unrealised profit and loss
     * reflects it.
     */
    public synchronized PortfolioSnapshot applyMark(InstrumentKey instrument,
                                                    BigDecimal price) {
        if (instrument == null || price == null || price.signum() <= 0) {
            return snapshot();
        }
        marks.put(instrument, price);
        return snapshot();
    }

    public synchronized void clear() {
        positions.clear();
        blocked.clear();
        marks.clear();
        realisedPnl = BigDecimal.ZERO;
    }

    public ExecutionConfig config() {
        return config;
    }

    private BigDecimal markOf(Position position) {
        BigDecimal mark = marks.get(position.instrument());
        return mark == null ? position.lastPrice() : mark;
    }

    private static String positionId(TradeOrder order) {
        return "POS-" + order.orderId();
    }

    /**
     * Margin currently reserved for working orders.
     */
    public synchronized BigDecimal blockedMarginTotal() {
        return blocked.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Average open notional per position, zero when there is none.
     */
    public synchronized BigDecimal averageOpenNotional() {
        PortfolioSnapshot snapshot = snapshot();
        if (snapshot.openPositionCount() == 0) {
            return BigDecimal.ZERO;
        }
        return snapshot.exposure().divide(BigDecimal.valueOf(snapshot.openPositionCount()),
                MathContext.DECIMAL128);
    }

    /**
     * Side needed to close the open position of a strategy and instrument, when one
     * exists.
     */
    public synchronized Optional<OrderSide> closingSide(String strategyId,
                                                        InstrumentKey instrument) {
        return position(strategyId, instrument).map(position ->
                position.direction() == PositionDirection.LONG
                        ? OrderSide.SELL
                        : OrderSide.BUY);
    }
}
