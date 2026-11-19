package com.tradingassistant.platform.api;

import com.tradingassistant.platform.PlatformTestFixtures;
import com.tradingassistant.platform.security.AuthService;
import com.tradingassistant.platform.security.RefreshTokenService;
import com.tradingassistant.platform.user.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    private final UserService users = mock(UserService.class);
    private final RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
    private final AuthService authService = mock(AuthService.class);
    private final MockMvc mockMvc = WebTestSupport.mvc(
            new UserController(users, refreshTokens, authService));

    @BeforeEach
    void signIn() {
        WebTestSupport.authenticate(PlatformTestFixtures.principal());
    }

    @AfterEach
    void signOut() {
        WebTestSupport.clearAuthentication();
    }

    @Test
    void profileIsReadFromTheAuthenticatedIdentity() throws Exception {
        when(users.require("user-1")).thenReturn(PlatformTestFixtures.user());

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("user-1"))
                .andExpect(jsonPath("$.data.email").value("user@example.com"))
                .andExpect(jsonPath("$.data.roles[0]").value("USER"));
    }

    @Test
    void profileCanBeRenamed() throws Exception {
        when(users.updateDisplayName("user-1", "New Name"))
                .thenReturn(PlatformTestFixtures.user().withProfile("New Name",
                        PlatformTestFixtures.NOW));

        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"New Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("New Name"));
    }

    @Test
    void passwordChangeRevokesEverySession() throws Exception {
        when(refreshTokens.revokeAll("user-1")).thenReturn(2L);

        mockMvc.perform(put("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password123\","
                                + "\"newPassword\":\"newpassword123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeRefreshTokens").value(0));

        verify(users).changePassword("user-1", "password123", "newpassword123");
        verify(refreshTokens).revokeAll("user-1");
    }

    @Test
    void sessionCountIsReported() throws Exception {
        when(refreshTokens.activeCount("user-1")).thenReturn(3L);

        mockMvc.perform(get("/api/v1/users/me/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeRefreshTokens").value(3));
    }

    @Test
    void logoutAllRevokesEverySession() throws Exception {
        when(authService.logoutAll("user-1")).thenReturn(4L);

        mockMvc.perform(put("/api/v1/users/me/logout-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeRefreshTokens").value(4));
    }

    @Test
    void shortNewPasswordIsRejected() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password123\","
                                + "\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());

        verify(users, org.mockito.Mockito.never()).changePassword(any(), any(), any());
    }
}
