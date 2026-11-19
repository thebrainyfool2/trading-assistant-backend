package com.tradingassistant.platform.history;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data repository for completed trade history.
 */
public interface TradeRecordRepository extends MongoRepository<TradeRecordDocument, String> {

    Page<TradeRecordDocument> findByUserIdOrderByRecordedAtDesc(String userId,
                                                                Pageable pageable);

    Page<TradeRecordDocument> findAllByOrderByRecordedAtDesc(Pageable pageable);

    boolean existsByTradeKey(String tradeKey);

    long countByUserId(String userId);
}
