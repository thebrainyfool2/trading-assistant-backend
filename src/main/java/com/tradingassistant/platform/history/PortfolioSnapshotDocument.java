package com.tradingassistant.platform.history;

import com.tradingassistant.execution.domain.PortfolioSnapshot;
import com.tradingassistant.execution.domain.Position;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Durable point-in-time copy of the engine portfolio.
 *
 * <p>The engine portfolio is a single in-process portfolio, so snapshots use a
 * fixed portfolio identity. No client can create or edit a snapshot.</p>
 */
@Document(collection = "portfolio_snapshots")
public record PortfolioSnapshotDocument(
        @Id String id,
        @Indexed String portfolioId,
        @Indexed Instant capturedAt,
        BigDecimal capital,
        BigDecimal availableCapital,
        BigDecimal blockedMargin,
        BigDecimal usedMargin,
        BigDecimal realisedPnl,
        BigDecimal unrealisedPnl,
        BigDecimal exposure,
        int openPositions,
        int closedPositions,
        List<PortfolioPositionSnapshot> positions) {

    public PortfolioSnapshotDocument {
        portfolioId = Objects.requireNonNull(portfolioId, "portfolioId must not be null");
        capturedAt = Objects.requireNonNull(capturedAt, "capturedAt must not be null");
        capital = Objects.requireNonNull(capital, "capital must not be null");
        availableCapital = Objects.requireNonNull(availableCapital,
                "availableCapital must not be null");
        blockedMargin = Objects.requireNonNull(blockedMargin,
                "blockedMargin must not be null");
        usedMargin = Objects.requireNonNull(usedMargin, "usedMargin must not be null");
        realisedPnl = Objects.requireNonNull(realisedPnl, "realisedPnl must not be null");
        unrealisedPnl = Objects.requireNonNull(unrealisedPnl,
                "unrealisedPnl must not be null");
        exposure = Objects.requireNonNull(exposure, "exposure must not be null");
        positions = positions == null ? List.of() : List.copyOf(positions);
    }

    public static PortfolioSnapshotDocument capture(String portfolioId,
                                                    PortfolioSnapshot snapshot,
                                                    Instant capturedAt) {
        List<PortfolioPositionSnapshot> positions = Stream.concat(
                        snapshot.openPositions().stream(),
                        snapshot.closedPositions().stream())
                .map(PortfolioPositionSnapshot::from)
                .toList();
        return new PortfolioSnapshotDocument(null, portfolioId, capturedAt,
                snapshot.capital(), snapshot.availableCapital(),
                snapshot.blockedMargin(), snapshot.usedMargin(),
                snapshot.realisedPnl(), snapshot.unrealisedPnl(), snapshot.exposure(),
                snapshot.openPositionCount(), snapshot.closedPositionCount(), positions);
    }
}
