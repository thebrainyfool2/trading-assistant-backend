package com.tradingassistant.platform.notification;

import com.tradingassistant.common.api.ErrorCode;
import com.tradingassistant.platform.error.PlatformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Persists in-app notifications.
 *
 * <p>Delivery is deliberately pull-based: clients read their notifications from
 * the API. A future push channel can subscribe to this service without changing
 * the modules that raise notifications, and a notification failure never breaks
 * the operation that raised it.</p>
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    public static final int MAXIMUM_PAGE_SIZE = 100;

    private final NotificationRepository repository;
    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    public NotificationService(NotificationRepository repository,
                               MongoTemplate mongoTemplate, Clock clock) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
    }

    /**
     * Records a notification. Persistence failures are logged, not propagated:
     * a notification must never fail the trade or login that triggered it.
     */
    public void notify(String userId, NotificationType type, String title,
                       String message) {
        try {
            repository.save(new NotificationDocument(null, userId, type, title, message,
                    false, clock.instant(), null));
        } catch (RuntimeException failure) {
            log.warn("Notification was not persisted for user {}: {}", userId,
                    failure.toString());
        }
    }

    public Page<NotificationDocument> list(String userId, int page, int size) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId,
                PageRequest.of(Math.max(page, 0), clampSize(size)));
    }

    public long unreadCount(String userId) {
        return repository.countByUserIdAndReadFalse(userId);
    }

    public NotificationDocument markRead(String userId, String notificationId) {
        NotificationDocument notification = repository
                .findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new PlatformException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Notification not found."));
        if (notification.read()) {
            return notification;
        }
        return repository.save(notification.markRead(clock.instant()));
    }

    public long markAllRead(String userId) {
        Query query = Query.query(Criteria.where("userId").is(userId)
                .and("read").is(false));
        return mongoTemplate.updateMulti(query,
                new Update().set("read", true).set("readAt", clock.instant()),
                NotificationDocument.class).getModifiedCount();
    }

    /**
     * Removes read notifications older than the retention window.
     */
    public long purgeReadBefore(Duration retention) {
        Instant cutoff = clock.instant().minus(retention);
        try {
            return repository.deleteByReadTrueAndCreatedAtBefore(cutoff);
        } catch (DataAccessException failure) {
            log.warn("Notification retention failed: {}", failure.getMessage());
            return 0;
        }
    }

    public static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAXIMUM_PAGE_SIZE);
    }
}
