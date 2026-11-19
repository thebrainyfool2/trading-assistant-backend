package com.tradingassistant.platform.executionapp;

import com.tradingassistant.execution.domain.PortfolioSnapshot;
import com.tradingassistant.platform.history.PortfolioPositionSnapshot;
import com.tradingassistant.platform.history.PortfolioSnapshotDocument;
import com.tradingassistant.platform.history.PortfolioSnapshotService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Serves the engine portfolio when it is live and falls back to the latest
 * persisted snapshot otherwise.
 *
 * <p>The response always states its source, so a client can distinguish live
 * engine state from a stored snapshot instead of assuming freshness.</p>
 */
@Service
public class PortfolioQueryService {

    public static final String SOURCE_LIVE = "LIVE";
    public static final String SOURCE_SNAPSHOT = "SNAPSHOT";
    public static final String SOURCE_UNAVAILABLE = "UNAVAILABLE";

    private final ExecutionGateway gateway;
    private final PortfolioSnapshotService snapshots;

    public PortfolioQueryService(ExecutionGateway gateway,
                                 PortfolioSnapshotService snapshots) {
        this.gateway = gateway;
        this.snapshots = snapshots;
    }

    public PortfolioView current() {
        Optional<PortfolioSnapshot> live = gateway.snapshot();
        if (live.isPresent()) {
            PortfolioSnapshot snapshot = live.get();
            return new PortfolioView(SOURCE_LIVE, null, snapshot.capital(),
                    snapshot.availableCapital(), snapshot.blockedMargin(),
                    snapshot.usedMargin(), snapshot.realisedPnl(),
                    snapshot.unrealisedPnl(), snapshot.exposure(),
                    snapshot.openPositionCount(), snapshot.closedPositionCount(),
                    concat(snapshot));
        }
        Optional<PortfolioSnapshotDocument> stored = snapshots.latest();
        if (stored.isPresent()) {
            PortfolioSnapshotDocument document = stored.get();
            return new PortfolioView(SOURCE_SNAPSHOT, document.capturedAt(),
                    document.capital(), document.availableCapital(),
                    document.blockedMargin(), document.usedMargin(),
                    document.realisedPnl(), document.unrealisedPnl(),
                    document.exposure(), document.openPositions(),
                    document.closedPositions(), document.positions());
        }
        return new PortfolioView(SOURCE_UNAVAILABLE, null, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, List.of());
    }

    public List<PortfolioPositionSnapshot> positions() {
        return current().positions().stream()
                .filter(position -> position.status() != null
                        && position.status() != com.tradingassistant.execution.domain.PositionStatus.CLOSED)
                .toList();
    }

    private static List<PortfolioPositionSnapshot> concat(PortfolioSnapshot snapshot) {
        return java.util.stream.Stream.concat(snapshot.openPositions().stream(),
                        snapshot.closedPositions().stream())
                .map(PortfolioPositionSnapshot::from)
                .toList();
    }

    /**
     * Normalised portfolio response independent of its source.
     */
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
            List<PortfolioPositionSnapshot> positions) {

        public PortfolioView {
            positions = positions == null ? List.of() : List.copyOf(positions);
        }
    }
}
