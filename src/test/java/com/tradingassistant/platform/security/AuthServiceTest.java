package com.tradingassistant.platform.security;

import com.tradingassistant.platform.PlatformTestFixtures;
import com.tradingassistant.platform.notification.NotificationService;
import com.tradingassistant.platform.notification.NotificationType;
import com.tradingassistant.platform.user.UserDocument;
import com.tradingassistant.platform.user.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final Instant NOW = PlatformTestFixtures.NOW;

    private final UserService users = mock(UserService.class);
    private final RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final AuthService authService = new AuthService(users, refreshTokens,
            jwtService, notifications, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void registerIssuesTokensAndWelcomeNotification() {
        UserDocument user = PlatformTestFixtures.user();
        when(users.register("user@example.com", "Test User", "password123"))
                .thenReturn(user);
        stubTokens(user);

        AuthService.AuthResult result = authService.register("user@example.com",
                "Test User", "password123");

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.user()).isSameAs(user);
        verify(notifications).notify(eq(user.id()), eq(NotificationType.SYSTEM_MESSAGE),
                eq("Welcome to Trading Assistant"), any(String.class));
    }

    @Test
    void loginRecordsLoginAlert() {
        UserDocument user = PlatformTestFixtures.user();
        when(users.authenticate("user@example.com", "password123")).thenReturn(user);
        stubTokens(user);

        AuthService.AuthResult result = authService.login("user@example.com",
                "password123");

        assertThat(result.user()).isSameAs(user);
        verify(notifications).notify(eq(user.id()), eq(NotificationType.LOGIN_ALERT),
                eq("New sign-in"), any(String.class));
    }

    @Test
    void refreshConsumesTokenAndReloadsUser() {
        UserDocument user = PlatformTestFixtures.user();
        when(refreshTokens.consume("refresh-value")).thenReturn(user.id());
        when(users.require(user.id())).thenReturn(user);
        stubTokens(user);

        AuthService.AuthResult result = authService.refresh("refresh-value");

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        verify(refreshTokens).consume("refresh-value");
    }

    @Test
    void issueCarriesStoredRolesInThePrincipal() {
        UserDocument user = PlatformTestFixtures.user("admin-1",
                "admin@example.com", Role.ADMIN, Role.USER);
        stubTokens(user);

        authService.issue(user);

        ArgumentCaptor<AuthenticatedUser> principal =
                ArgumentCaptor.forClass(AuthenticatedUser.class);
        verify(jwtService).issue(principal.capture());
        assertThat(principal.getValue().roles()).contains(Role.ADMIN, Role.USER);
        verify(refreshTokens).issue(user.id());
    }

    @Test
    void logoutRevokesThePresentedToken() {
        authService.logout("refresh-value");

        verify(refreshTokens).revoke("refresh-value");
    }

    @Test
    void logoutAllRevokesEverySession() {
        when(refreshTokens.revokeAll("user-1")).thenReturn(3L);

        assertThat(authService.logoutAll("user-1")).isEqualTo(3L);
    }

    private void stubTokens(UserDocument user) {
        when(jwtService.issue(any(AuthenticatedUser.class))).thenReturn(
                new JwtService.IssuedAccessToken("access-token", NOW.plusSeconds(900)));
        when(refreshTokens.issue(user.id())).thenReturn(
                new RefreshTokenService.IssuedRefreshToken("refresh-token",
                        NOW.plusSeconds(86_400)));
    }
}
