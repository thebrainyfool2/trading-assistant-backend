package com.tradingassistant.platform.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and validates HMAC-SHA256 access tokens.
 *
 * <p>Tokens carry the user identity, email and roles. They are stateless: the
 * server keeps no session, so a token is accepted until it expires. Refresh
 * tokens are opaque and stored hashed in MongoDB, which is what makes
 * revocation and rotation possible.</p>
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final int MINIMUM_KEY_BYTES = 32;
    private static final String ROLES_CLAIM = "roles";
    private static final String EMAIL_CLAIM = "email";

    private final SecurityProperties properties;
    private final Clock clock;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public JwtService(SecurityProperties properties, Environment environment, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        SecretKey key = resolveKey(properties, environment);
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        NimbusJwtDecoder nimbusDecoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        nimbusDecoder.setJwtValidator(
                JwtValidators.createDefaultWithIssuer(properties.issuer()));
        this.decoder = nimbusDecoder;
    }

    /**
     * Issues a signed access token for an authenticated user.
     */
    public IssuedAccessToken issue(AuthenticatedUser user) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.userId())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim(EMAIL_CLAIM, user.email())
                .claim(ROLES_CLAIM, user.roles().stream().map(Enum::name).sorted().toList())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
        return new IssuedAccessToken(value, expiresAt);
    }

    /**
     * Verifies a token's signature, issuer and expiry and reconstructs the
     * principal. Any failure yields an empty result; the caller decides how to
     * answer.
     */
    public Optional<AuthenticatedUser> authenticate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Jwt jwt = decoder.decode(token.trim());
            String userId = jwt.getSubject();
            String email = jwt.getClaimAsString(EMAIL_CLAIM);
            List<String> roles = jwt.getClaimAsStringList(ROLES_CLAIM);
            if (userId == null || userId.isBlank() || email == null || email.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(AuthenticatedUser.of(userId, email,
                    roles == null ? List.of() : roles));
        } catch (JwtException | IllegalArgumentException failure) {
            log.debug("Rejected access token: {}", failure.getMessage());
            return Optional.empty();
        }
    }

    public long accessTokenTtlSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }

    private static SecretKey resolveKey(SecurityProperties properties,
                                        Environment environment) {
        if (properties.jwtSecret().isBlank()) {
            if (Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
                throw new IllegalStateException(
                        "JWT_SECRET must be configured before the production profile starts");
            }
            byte[] generated = new byte[MINIMUM_KEY_BYTES];
            new SecureRandom().nextBytes(generated);
            log.warn("JWT_SECRET is not configured; using an ephemeral development key. "
                    + "Issued tokens are invalid after restart.");
            return new SecretKeySpec(generated, "HmacSHA256");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(properties.jwtSecret());
        } catch (IllegalArgumentException failure) {
            decoded = properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
        }
        if (decoded.length < MINIMUM_KEY_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must decode to at least 256 bits (32 bytes)");
        }
        return new SecretKeySpec(decoded, "HmacSHA256");
    }

    /**
     * One signed access token and its expiry instant.
     */
    public record IssuedAccessToken(String value, Instant expiresAt) {
    }
}
