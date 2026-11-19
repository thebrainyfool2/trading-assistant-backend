package com.tradingassistant.platform.security;

import com.tradingassistant.common.api.ErrorCode;
import com.tradingassistant.platform.error.PlatformException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Issues, rotates and revokes opaque refresh tokens.
 *
 * <p>Rotation is atomic: a token can be consumed exactly once even when two
 * clients present it at the same instant. A consumed token is revoked before the
 * replacement is written, so replay fails rather than extending a session.</p>
 */
@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final MongoTemplate mongoTemplate;
    private final SecurityProperties properties;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository,
                               MongoTemplate mongoTemplate,
                               SecurityProperties properties,
                               Clock clock) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Issues a new refresh token for the user.
     */
    public IssuedRefreshToken issue(String userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.refreshTokenTtl());
        repository.save(new RefreshTokenDocument(null, userId, hash(raw), now,
                expiresAt, null));
        return new IssuedRefreshToken(raw, expiresAt);
    }

    /**
     * Atomically consumes a refresh token and returns its owner.
     *
     * @throws PlatformException when the token is unknown, expired or already used
     */
    public String consume(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidToken();
        }
        Instant now = clock.instant();
        Query query = Query.query(Criteria.where("tokenHash").is(hash(rawToken.trim()))
                .and("revokedAt").is(null)
                .and("expiresAt").gt(now));
        Update update = new Update().set("revokedAt", now);
        RefreshTokenDocument consumed = mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(false),
                RefreshTokenDocument.class);
        if (consumed == null) {
            throw invalidToken();
        }
        return consumed.userId();
    }

    /**
     * Revokes one token when it is known. Logout is idempotent and never reveals
     * whether the token existed.
     */
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        Query query = Query.query(Criteria.where("tokenHash").is(hash(rawToken.trim()))
                .and("revokedAt").is(null));
        mongoTemplate.updateFirst(query, new Update().set("revokedAt", clock.instant()),
                RefreshTokenDocument.class);
    }

    public long revokeAll(String userId) {
        Query query = Query.query(Criteria.where("userId").is(userId)
                .and("revokedAt").is(null));
        return mongoTemplate.updateMulti(query,
                new Update().set("revokedAt", clock.instant()),
                RefreshTokenDocument.class).getModifiedCount();
    }

    /**
     * Deletes expired tokens and revoked tokens older than the retention window.
     */
    public long cleanup(Duration revokedRetention) {
        Instant now = clock.instant();
        long deleted = repository.deleteByExpiresAtBefore(now);
        deleted += repository.deleteByRevokedAtBefore(now.minus(revokedRetention));
        return deleted;
    }

    public long activeCount(String userId) {
        Instant now = clock.instant();
        return repository.findByUserIdOrderByIssuedAtDesc(userId).stream()
                .filter(token -> token.active(now))
                .count();
    }

    public static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is not available", failure);
        }
    }

    private static PlatformException invalidToken() {
        return new PlatformException(ErrorCode.UNAUTHORIZED,
                "The refresh token is invalid or has expired.");
    }

    /**
     * A raw refresh token returned once to the client and its expiry instant.
     */
    public record IssuedRefreshToken(String value, Instant expiresAt) {
    }
}
