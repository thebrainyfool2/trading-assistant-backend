package com.tradingassistant.platform.api;

import com.tradingassistant.common.api.ApiResponse;
import com.tradingassistant.platform.api.dto.SettingsDtos;
import com.tradingassistant.platform.preferences.UserPreferencesService;
import com.tradingassistant.platform.security.AuthenticatedUser;
import com.tradingassistant.platform.settings.ApplicationSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Client preferences and administrator-managed platform settings.
 */
@RestController
@RequestMapping(path = "/api/v1/settings", produces = MediaType.APPLICATION_JSON_VALUE)
public class SettingsController {

    private final UserPreferencesService preferences;
    private final ApplicationSettingsService applicationSettings;

    public SettingsController(UserPreferencesService preferences,
                              ApplicationSettingsService applicationSettings) {
        this.preferences = preferences;
        this.applicationSettings = applicationSettings;
    }

    @GetMapping("/preferences")
    public ApiResponse<SettingsDtos.PreferencesView> preferences(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(SettingsDtos.PreferencesView.from(
                preferences.getOrCreate(user.userId())));
    }

    @PutMapping(path = "/preferences", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<SettingsDtos.PreferencesView> updatePreferences(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody SettingsDtos.UpdatePreferencesRequest request) {
        return ApiResponse.success(SettingsDtos.PreferencesView.from(
                preferences.update(user.userId(), request.theme(),
                        request.defaultInterval(), request.defaultStrategyId(),
                        request.notificationsEnabled(), request.orderNotifications(),
                        request.positionNotifications(), request.loginAlerts(),
                        request.systemMessages())));
    }

    @GetMapping("/application")
    public ApiResponse<SettingsDtos.ApplicationSettingsView> application() {
        return ApiResponse.success(SettingsDtos.ApplicationSettingsView.from(
                applicationSettings.get()));
    }

    @PutMapping(path = "/application", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SettingsDtos.ApplicationSettingsView> updateApplication(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody SettingsDtos.UpdateApplicationSettingsRequest request) {
        return ApiResponse.success(SettingsDtos.ApplicationSettingsView.from(
                applicationSettings.update(user.userId(), request.maintenanceMode(),
                        request.maximumWatchlistItems(),
                        request.historyRetentionDays())));
    }
}
