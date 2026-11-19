package com.tradingassistant.platform.history;

import com.tradingassistant.execution.domain.PortfolioSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;

/**
 * Captures and reads durable portfolio snapshots.
 *
 * <p>Capturing is best-effort: a database problem must not stop the engine from
 * processing market data.</p>
 */
@Service
public class PortfolioSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioSnapshotService.class);
    public static final String PORTFOLIO_ID = "engine";

    private final PortfolioSnapshotRepository repository;
    private final Clock clock;

    public PortfolioSnapshotService(PortfolioSnapshotRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Optional<PortfolioSnapshotDocument> capture(PortfolioSnapshot snapshot) {
        if (snapshot == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(repository.save(PortfolioSnapshotDocument.capture(
                    PORTFOLIO_ID, snapshot, clock.instant())));
        } catch (RuntimeException failure) {
            log.warn("Portfolio snapshot was not persisted: {}", failure.toString());
            return Optional.empty();
        }
    }

    public Optional<PortfolioSnapshotDocument> latest() {
        return repository.findFirstByPortfolioIdOrderByCapturedAtDesc(PORTFOLIO_ID);
    }

    public Page<PortfolioSnapshotDocument> history(int page, int size) {
        return repository.findByPortfolioIdOrderByCapturedAtDesc(PORTFOLIO_ID,
                PageRequest.of(Math.max(page, 0),
                        Math.min(Math.max(size, 1), OrderHistoryService.MAXIMUM_PAGE_SIZE)));
    }
}
