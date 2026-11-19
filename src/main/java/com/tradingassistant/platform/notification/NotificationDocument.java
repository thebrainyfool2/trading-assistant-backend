package com.tradingassistant.platform.notification;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Objects;

/**
 * One persisted in-app notification.
 *
 * @param id        document identity
 * @param userId    recipient
 * @param type      category
 * @param title     short title
 * @param message   readable message
 * @param read      whether the recipient has read it
 * @param createdAt creation instant
 * @param readAt    instant the notification was read, when it was
 */
@Document(collection = "notifications")
public record NotificationDocument(
        @Id String id,
        @Indexed String userId,
        NotificationType type,
        String title,
        String message,
        boolean read,
        Instant createdAt,
        Instant readAt) {

    public NotificationDocument {
        userId = Objects.requireNonNull(userId, "userId must not be null");
        type = Objects.requireNonNull(type, "type must not be null");
        title = requireText(title, "title", 120);
        message = requireText(message, "message", 500);
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public NotificationDocument markRead(Instant at) {
        return new NotificationDocument(id, userId, type, title, message, true,
                createdAt, at);
    }

    private static String requireText(String value, String field, int maximum) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maximum) {
            throw new IllegalArgumentException(field + " must not exceed " + maximum
                    + " characters");
        }
        return trimmed;
    }
}
