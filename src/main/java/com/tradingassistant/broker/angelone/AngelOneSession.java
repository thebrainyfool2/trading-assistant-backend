package com.tradingassistant.broker.angelone;

import java.time.Duration;
import java.time.Instant;

/**
 * Internal token-bearing session. This type is deliberately absent from the
 * public broker API and its {@link #toString()} never prints credentials.
 */
public final class AngelOneSession {

    private final String clientCode;
    private final String accessToken;
    private final String refreshToken;
    private final String feedToken;
    private final Instant issuedAt;
    private final Instant expiresAt;

    public AngelOneSession(String clientCode,
                           String accessToken,
                           String refreshToken,
                           String feedToken,
                           Instant issuedAt,
                           Instant expiresAt) {
        this.clientCode = clientCode;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.feedToken = feedToken;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public String clientCode() {
        return clientCode;
    }

    public String accessToken() {
        return accessToken;
    }

    public String refreshToken() {
        return refreshToken;
    }

    public String feedToken() {
        return feedToken;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public boolean canRefresh() {
        return refreshToken != null && !refreshToken.isBlank();
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isExpiring(Instant now, Duration skew) {
        return !expiresAt.minus(skew).isAfter(now);
    }

    @Override
    public String toString() {
        return "AngelOneSession[clientCode=***, issuedAt=" + issuedAt
                + ", expiresAt=" + expiresAt + ", refreshable=" + canRefresh() + "]";
    }
}
