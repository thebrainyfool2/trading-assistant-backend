package com.tradingassistant.platform.security;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Authentication and authorization configuration.
 *
 * <p>The signing secret is never committed. It is supplied at runtime through
 * {@code JWT_SECRET} as a Base64-encoded value of at least 256 bits. Development
 * profiles generate an ephemeral secret when it is absent so the operational
 * endpoints keep starting; the {@code prod} profile refuses to start without
 * one.</p>
 *
 * @param jwtSecret              Base64 HMAC secret; blank only outside production
 * @param issuer                 issuer claim written to and required from tokens
 * @param accessTokenTtl         lifetime of an access token
 * @param refreshTokenTtl        lifetime of an opaque refresh token
 * @param bcryptStrength         BCrypt cost factor
 * @param bootstrapAdminEmail    optional administrator created on first start
 * @param bootstrapAdminPassword optional administrator password, from the environment only
 * @param allowedOrigins         comma-separated CORS origins, empty by default
 */
@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        String jwtSecret,
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        @Min(4) @Max(31) int bcryptStrength,
        String bootstrapAdminEmail,
        String bootstrapAdminPassword,
        String allowedOrigins) {

    public SecurityProperties {
        jwtSecret = jwtSecret == null ? "" : jwtSecret.trim();
        issuer = issuer == null ? "" : issuer.trim();
        allowedOrigins = allowedOrigins == null ? "" : allowedOrigins.trim();
        bootstrapAdminEmail = bootstrapAdminEmail == null ? "" : bootstrapAdminEmail.trim();
        bootstrapAdminPassword = bootstrapAdminPassword == null
                ? "" : bootstrapAdminPassword;
    }

    public List<String> corsOrigins() {
        if (allowedOrigins.isBlank()) {
            return List.of();
        }
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    public boolean bootstrapAdminConfigured() {
        return !bootstrapAdminEmail.isBlank() && !bootstrapAdminPassword.isBlank();
    }
}
