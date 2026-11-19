package com.tradingassistant.platform.security;

import com.tradingassistant.platform.notification.NotificationService;
import com.tradingassistant.platform.notification.NotificationType;
import com.tradingassistant.platform.user.UserDocument;
import com.tradingassistant.platform.user.UserService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Registration, login, refresh and logout.
 *
 * <p>Access tokens are stateless; refresh tokens are rotated on every use and
 * revoked on logout. A login raises a notification so the account owner can see
 * that a new session started.</p>
 */
@Service
public class AuthService {

    private final UserService users;
    private final RefreshTokenService refreshTokens;
    private final JwtService jwtService;
    private final NotificationService notifications;
    private final Clock clock;

    public AuthService(UserService users, RefreshTokenService refreshTokens,
                       JwtService jwtService, NotificationService notifications,
                       Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.jwtService = jwtService;
        this.notifications = notifications;
        this.clock = clock;
    }

    public AuthResult register(String email, String displayName, String password) {
        UserDocument user = users.register(email, displayName, password);
        AuthResult result = issue(user);
        notifications.notify(user.id(), NotificationType.SYSTEM_MESSAGE,
                "Welcome to Trading Assistant",
                "Your account was created. Market analysis is available after sign-in.");
        return result;
    }

    public AuthResult login(String email, String password) {
        UserDocument user = users.authenticate(email, password);
        AuthResult result = issue(user);
        Instant at = clock.instant();
        notifications.notify(user.id(), NotificationType.LOGIN_ALERT,
                "New sign-in",
                "A new session signed in at " + at + " UTC.");
        return result;
    }

    /**
     * Rotates a refresh token. The presented token is consumed before the
     * replacement is issued, so replaying it fails.
     */
    public AuthResult refresh(String rawRefreshToken) {
        String userId = refreshTokens.consume(rawRefreshToken);
        UserDocument user = users.require(userId);
        return issue(user);
    }

    public void logout(String rawRefreshToken) {
        refreshTokens.revoke(rawRefreshToken);
    }

    public long logoutAll(String userId) {
        return refreshTokens.revokeAll(userId);
    }

    public AuthResult issue(UserDocument user) {
        AuthenticatedUser principal = new AuthenticatedUser(user.id(), user.email(),
                user.roles());
        JwtService.IssuedAccessToken access = jwtService.issue(principal);
        RefreshTokenService.IssuedRefreshToken refresh = refreshTokens.issue(user.id());
        return new AuthResult(user, access.value(), access.expiresAt(), refresh.value(),
                refresh.expiresAt());
    }

    /**
     * A complete credential set returned to a client.
     */
    public record AuthResult(UserDocument user, String accessToken, Instant accessExpiresAt,
                             String refreshToken, Instant refreshExpiresAt) {
    }
}
