package com.tradingassistant.platform.api.dto;

import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.platform.preferences.Theme;
import com.tradingassistant.platform.preferences.UserPreferencesDocument;
import com.tradingassistant.platform.settings.ApplicationSettingsDocument;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Client preferences and administrator settings contract.
 */
public final class SettingsDtos {

    private SettingsDtos() {
    }

    public record PreferencesView(
            Theme theme,
            Interval defaultInterval,
            String defaultStrategyId,
            boolean notificationsEnabled,
            boolean orderNotifications,
            boolean positionNotifications,
            boolean loginAlerts,
            boolean systemMessages,
            Instant updatedAt) {

        public static PreferencesView from(UserPreferencesDocument preferences) {
            return new PreferencesView(preferences.theme(),
                    preferences.defaultInterval(), preferences.defaultStrategyId(),
                    preferences.notificationsEnabled(),
                    preferences.orderNotifications(),
                    preferences.positionNotifications(),
                    preferences.loginAlerts(), preferences.systemMessages(),
                    preferences.updatedAt());
        }
    }

    public record UpdatePreferencesRequest(
            @NotNull Theme theme,
            @NotNull Interval defaultInterval,
            @Size(max = 64) String defaultStrategyId,
            boolean notificationsEnabled,
            boolean orderNotifications,
            boolean positionNotifications,
            boolean loginAlerts,
            boolean systemMessages) {
    }

    public record ApplicationSettingsView(
            boolean maintenanceMode,
            int maximumWatchlistItems,
            int historyRetentionDays,
            Instant updatedAt,
            String updatedBy) {

        public static ApplicationSettingsView from(
                ApplicationSettingsDocument settings) {
            return new ApplicationSettingsView(settings.maintenanceMode(),
                    settings.maximumWatchlistItems(), settings.historyRetentionDays(),
                    settings.updatedAt(), settings.updatedBy());
        }
    }

    public record UpdateApplicationSettingsRequest(
            boolean maintenanceMode,
            @Min(1) @Max(100) int maximumWatchlistItems,
            @Min(30) @Max(3650) int historyRetentionDays) {
    }
}
