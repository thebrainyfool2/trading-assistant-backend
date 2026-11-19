package com.tradingassistant.platform.history;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Spring Data repository for portfolio snapshots.
 */
public interface PortfolioSnapshotRepository
        extends MongoRepository<PortfolioSnapshotDocument, String> {

    Page<PortfolioSnapshotDocument> findByPortfolioIdOrderByCapturedAtDesc(
            String portfolioId, Pageable pageable);

    Optional<PortfolioSnapshotDocument> findFirstByPortfolioIdOrderByCapturedAtDesc(
            String portfolioId);
}
