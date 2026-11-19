package com.tradingassistant.platform.security;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Objects;

/**
 * A stored refresh token.
 *
 * <p>Only the SHA-256 hash of the opaque token is persisted. The database
 * therefore cannot be used to obtain a usable credential, and a token can still
 * be found and revoked by hashing the presented value.</p>
 *
 * @param id        document identity
 * @param userId    owner
 * @param tokenHash SHA-256 hash of the token, unique
 * @param issuedAt  issue instant
 * @param expiresAt expiry instant
 * @param revokedAt revocation instant, null while active
 */
@Document(collection = "refresh_tokens")
public record RefreshTokenDocument(
        @Id String id,
        @Indexed String userId,
        @Indexed(unique = true) String tokenHash,
        Instant issuedAt,
        @Indexed Instant expiresAt,
        Instant revokedAt) {

    public RefreshTokenDocument {
        userId = Objects.requireNonNull(userId, "userId must not be null");
        tokenHash = Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        issuedAt = Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public boolean active(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
