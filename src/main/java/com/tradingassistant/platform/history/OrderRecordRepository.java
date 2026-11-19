package com.tradingassistant.platform.history;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Spring Data repository for order history.
 */
public interface OrderRecordRepository extends MongoRepository<OrderRecordDocument, String> {

    Page<OrderRecordDocument> findByUserIdOrderByCreatedAtDesc(String userId,
                                                               Pageable pageable);

    Page<OrderRecordDocument> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Optional<OrderRecordDocument> findByIdAndUserId(String id, String userId);

    Optional<OrderRecordDocument> findByEngineOrderId(String engineOrderId);

    long countByUserId(String userId);
}
