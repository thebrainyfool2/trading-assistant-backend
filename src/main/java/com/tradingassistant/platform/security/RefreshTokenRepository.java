package com.tradingassistant.platform.security;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for refresh tokens.
 */
public interface RefreshTokenRepository extends MongoRepository<RefreshTokenDocument, String> {

    Optional<RefreshTokenDocument> findByTokenHash(String tokenHash);

    List<RefreshTokenDocument> findByUserIdOrderByIssuedAtDesc(String userId);

    long deleteByExpiresAtBefore(Instant cutoff);

    long deleteByRevokedAtBefore(Instant cutoff);

    long deleteByUserId(String userId);
}
