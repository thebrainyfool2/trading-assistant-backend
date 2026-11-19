package com.tradingassistant.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = base64Secret();

    @Test
    void issuedTokenCarriesIdentityAndRoles() {
        JwtService service = service("issuer-a", Duration.ofMinutes(15), SECRET);
        AuthenticatedUser user = new AuthenticatedUser("user-1", "user@example.com",
                Set.of(Role.USER, Role.ADMIN));

        JwtService.IssuedAccessToken token = service.issue(user);
        AuthenticatedUser parsed = service.authenticate(token.value()).orElseThrow();

        assertThat(parsed.userId()).isEqualTo("user-1");
        assertThat(parsed.email()).isEqualTo("user@example.com");
        assertThat(parsed.roles()).containsExactlyInAnyOrder(Role.USER, Role.ADMIN);
        assertThat(token.expiresAt()).isAfter(Clock.systemUTC().instant());
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService service = service("issuer-a", Duration.ofMinutes(15), SECRET);
        String token = service.issue(principal()).value();
        char replacement = token.charAt(token.length() - 1) == 'a' ? 'b' : 'a';

        assertThat(service.authenticate(
                token.substring(0, token.length() - 1) + replacement)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() throws InterruptedException {
        JwtService service = service("issuer-a", Duration.ofMillis(1), SECRET);
        String token = service.issue(principal()).value();
        Thread.sleep(25);

        assertThat(service.authenticate(token)).isEmpty();
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        JwtService issuerA = service("issuer-a", Duration.ofMinutes(15), SECRET);
        JwtService issuerB = service("issuer-b", Duration.ofMinutes(15), SECRET);
        String token = issuerA.issue(principal()).value();

        assertThat(issuerB.authenticate(token)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtService signer = service("issuer-a", Duration.ofMinutes(15), SECRET);
        JwtService verifier = service("issuer-a", Duration.ofMinutes(15),
                base64Secret());
        String token = signer.issue(principal()).value();

        assertThat(verifier.authenticate(token)).isEmpty();
    }

    @Test
    void blankTokenIsRejected() {
        JwtService service = service("issuer-a", Duration.ofMinutes(15), SECRET);

        assertThat(service.authenticate(null)).isEmpty();
        assertThat(service.authenticate("   ")).isEmpty();
    }

    @Test
    void developmentKeyIsGeneratedWhenSecretIsAbsent() {
        JwtService service = service("issuer-a", Duration.ofMinutes(15), "");
        String token = service.issue(principal()).value();

        assertThat(service.authenticate(token)).isPresent();
    }

    @Test
    void shortSecretIsRefused() {
        String shortSecret = Base64.getEncoder()
                .encodeToString("too-short".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service("issuer-a", Duration.ofMinutes(15), shortSecret))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
    }

    @Test
    void accessTokenTtlIsExposedInSeconds() {
        JwtService service = service("issuer-a", Duration.ofMinutes(15), SECRET);

        assertThat(service.accessTokenTtlSeconds()).isEqualTo(900);
    }

    private static JwtService service(String issuer, Duration ttl, String secret) {
        SecurityProperties properties = new SecurityProperties(secret, issuer, ttl,
                Duration.ofDays(30), 4, "", "", "");
        return new JwtService(properties, new MockEnvironment(), Clock.systemUTC());
    }

    private static AuthenticatedUser principal() {
        return new AuthenticatedUser("user-1", "user@example.com", Set.of(Role.USER));
    }

    private static String base64Secret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
