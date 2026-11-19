package com.tradingassistant.platform.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.Optional;

/**
 * Spring Data repository for notifications.
 */
public interface NotificationRepository extends MongoRepository<NotificationDocument, String> {

    Page<NotificationDocument> findByUserIdOrderByCreatedAtDesc(String userId,
                                                                Pageable pageable);

    long countByUserIdAndReadFalse(String userId);

    Optional<NotificationDocument> findByIdAndUserId(String id, String userId);

    long deleteByReadTrueAndCreatedAtBefore(Instant cutoff);
}
