package com.tradingassistant.platform.preferences;

import com.tradingassistant.market.domain.Interval;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Objects;

/**
 * Per-account client preferences.
 *
 * <p>The user identity is the document identity, so a preference document can
 * never belong to two accounts.</p>
 */
@Document(collection = "user_preferences")
public record UserPreferencesDocument(
        @Id String userId,
        Theme theme,
        Interval defaultInterval,
        String defaultStrategyId,
        boolean notificationsEnabled,
        boolean orderNotifications,
        boolean positionNotifications,
        boolean loginAlerts,
        boolean systemMessages,
        Instant updatedAt) {

    public UserPreferencesDocument {
        userId = Objects.requireNonNull(userId, "userId must not be null");
        theme = theme == null ? Theme.DARK : theme;
        defaultInterval = defaultInterval == null ? Interval.FIFTEEN_MINUTE : defaultInterval;
        defaultStrategyId = defaultStrategyId == null ? "" : defaultStrategyId.trim();
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public static UserPreferencesDocument defaults(String userId, Instant at) {
        return new UserPreferencesDocument(userId, Theme.DARK, Interval.FIFTEEN_MINUTE,
                "", true, true, true, true, true, at);
    }

    public UserPreferencesDocument update(Theme newTheme, Interval interval,
                                          String strategyId, boolean notifications,
                                          boolean orders, boolean positions,
                                          boolean logins, boolean system,
                                          Instant at) {
        return new UserPreferencesDocument(userId, newTheme, interval, strategyId,
                notifications, orders, positions, logins, system, at);
    }
}
