package com.tradingassistant.platform.watchlist;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for watchlists.
 */
public interface WatchlistRepository extends MongoRepository<WatchlistDocument, String> {

    List<WatchlistDocument> findByUserIdOrderByCreatedAtAsc(String userId);

    Optional<WatchlistDocument> findByIdAndUserId(String id, String userId);

    boolean existsByUserIdAndName(String userId, String name);

    long countByUserId(String userId);
}
