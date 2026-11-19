package com.tradingassistant.platform.api.dto;

import com.tradingassistant.platform.notification.NotificationDocument;
import com.tradingassistant.platform.notification.NotificationType;

import java.time.Instant;

/**
 * Notification API contract.
 */
public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationView(
            String id,
            NotificationType type,
            String title,
            String message,
            boolean read,
            Instant createdAt,
            Instant readAt) {

        public static NotificationView from(NotificationDocument notification) {
            return new NotificationView(notification.id(), notification.type(),
                    notification.title(), notification.message(), notification.read(),
                    notification.createdAt(), notification.readAt());
        }
    }

    public record UnreadCountView(long unread) {
    }
}
