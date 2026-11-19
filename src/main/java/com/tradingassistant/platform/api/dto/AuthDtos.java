package com.tradingassistant.platform.api.dto;

import com.tradingassistant.platform.security.AuthService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Authentication API contract.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 80) String displayName,
            @NotBlank @Size(min = 8, max = 128) String password) {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record LogoutRequest(@NotBlank String refreshToken) {
    }

    public record AuthResponse(
            String accessToken,
            String tokenType,
            long expiresInSeconds,
            Instant accessExpiresAt,
            String refreshToken,
            Instant refreshExpiresAt,
            UserDtos.UserView user) {

        public static AuthResponse from(AuthService.AuthResult result) {
            return new AuthResponse(result.accessToken(), "Bearer",
                    Math.max(0, result.accessExpiresAt().getEpochSecond()
                            - Instant.now().getEpochSecond()),
                    result.accessExpiresAt(), result.refreshToken(),
                    result.refreshExpiresAt(), UserDtos.UserView.from(result.user()));
        }
    }

    public record LogoutResponse(boolean revoked, String message) {

        public static LogoutResponse done() {
            return new LogoutResponse(true, "Signed out.");
        }
    }
}
