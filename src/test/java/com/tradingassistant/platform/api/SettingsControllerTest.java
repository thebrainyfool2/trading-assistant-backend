package com.tradingassistant.platform.api;

import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.platform.PlatformTestFixtures;
import com.tradingassistant.platform.preferences.Theme;
import com.tradingassistant.platform.preferences.UserPreferencesDocument;
import com.tradingassistant.platform.preferences.UserPreferencesService;
import com.tradingassistant.platform.settings.ApplicationSettingsDocument;
import com.tradingassistant.platform.settings.ApplicationSettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SettingsControllerTest {

    private final UserPreferencesService preferences = mock(UserPreferencesService.class);
    private final ApplicationSettingsService applicationSettings =
            mock(ApplicationSettingsService.class);
    private final MockMvc mockMvc = WebTestSupport.mvc(
            new SettingsController(preferences, applicationSettings));

    @BeforeEach
    void signIn() {
        WebTestSupport.authenticate(PlatformTestFixtures.principal());
    }

    @AfterEach
    void signOut() {
        WebTestSupport.clearAuthentication();
    }

    @Test
    void preferencesAreCreatedOnFirstRead() throws Exception {
        when(preferences.getOrCreate("user-1")).thenReturn(
                UserPreferencesDocument.defaults("user-1", PlatformTestFixtures.NOW));

        mockMvc.perform(get("/api/v1/settings/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.theme").value("DARK"))
                .andExpect(jsonPath("$.data.defaultInterval").value("FIFTEEN_MINUTE"));
    }

    @Test
    void preferencesCanBeUpdated() throws Exception {
        when(preferences.update(eq("user-1"), eq(Theme.DARK), eq(Interval.FIVE_MINUTE),
                eq("EMA_TREND"), eq(true), eq(false), eq(true), eq(true), eq(false)))
                .thenReturn(new UserPreferencesDocument("user-1", Theme.DARK,
                        Interval.FIVE_MINUTE, "EMA_TREND", true, false, true, true,
                        false, PlatformTestFixtures.NOW));

        mockMvc.perform(put("/api/v1/settings/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"DARK\",\"defaultInterval\":\"FIVE_MINUTE\","
                                + "\"defaultStrategyId\":\"EMA_TREND\","
                                + "\"notificationsEnabled\":true,"
                                + "\"orderNotifications\":false,"
                                + "\"positionNotifications\":true,"
                                + "\"loginAlerts\":true,\"systemMessages\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultInterval").value("FIVE_MINUTE"))
                .andExpect(jsonPath("$.data.systemMessages").value(false));
    }

    @Test
    void applicationSettingsAreReadable() throws Exception {
        when(applicationSettings.get()).thenReturn(
                ApplicationSettingsDocument.defaults("system",
                        PlatformTestFixtures.NOW));

        mockMvc.perform(get("/api/v1/settings/application"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maximumWatchlistItems").value(100));
    }

    @Test
    void applicationSettingsCanBeUpdatedByAdminSlice() throws Exception {
        when(applicationSettings.update("user-1", true, 50, 90))
                .thenReturn(ApplicationSettingsDocument.defaults("user-1",
                        PlatformTestFixtures.NOW).update(true, 50, 90, "user-1",
                        PlatformTestFixtures.NOW));

        mockMvc.perform(put("/api/v1/settings/application")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maintenanceMode\":true,"
                                + "\"maximumWatchlistItems\":50,"
                                + "\"historyRetentionDays\":90}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maintenanceMode").value(true))
                .andExpect(jsonPath("$.data.maximumWatchlistItems").value(50));

        verify(applicationSettings).update("user-1", true, 50, 90);
    }

    @Test
    void invalidApplicationSettingsAreRejected() throws Exception {
        mockMvc.perform(put("/api/v1/settings/application")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maintenanceMode\":false,"
                                + "\"maximumWatchlistItems\":0,"
                                + "\"historyRetentionDays\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }
}
