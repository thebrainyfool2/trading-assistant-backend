package com.tradingassistant.platform.settings;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Objects;

/**
 * Administrator-managed platform settings.
 *
 * <p>A single document holds operational preferences that are not secrets. It
 * deliberately excludes credentials and engine configuration, which remain
 * environment-owned.</p>
 */
@Document(collection = "application_settings")
public record ApplicationSettingsDocument(
        @Id String id,
        boolean maintenanceMode,
        int maximumWatchlistItems,
        int historyRetentionDays,
        Instant updatedAt,
        String updatedBy) {

    public static final String SINGLETON_ID = "application";

    public ApplicationSettingsDocument {
        id = id == null ? SINGLETON_ID : id;
        if (maximumWatchlistItems < 1 || maximumWatchlistItems > 100) {
            throw new IllegalArgumentException(
                    "maximumWatchlistItems must be between 1 and 100");
        }
        if (historyRetentionDays < 30 || historyRetentionDays > 3650) {
            throw new IllegalArgumentException(
                    "historyRetentionDays must be between 30 and 3650");
        }
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        updatedBy = updatedBy == null ? "" : updatedBy;
    }

    public static ApplicationSettingsDocument defaults(String updatedBy, Instant at) {
        return new ApplicationSettingsDocument(SINGLETON_ID, false, 100, 365, at,
                updatedBy);
    }

    public ApplicationSettingsDocument update(boolean newMaintenanceMode,
                                              int newMaximumWatchlistItems,
                                              int newHistoryRetentionDays,
                                              String adminId, Instant at) {
        return new ApplicationSettingsDocument(SINGLETON_ID, newMaintenanceMode,
                newMaximumWatchlistItems, newHistoryRetentionDays, at, adminId);
    }
}
