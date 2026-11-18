package com.tradingassistant.broker.angelone;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AngelOneSessionTest {

    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");

    @Test
    void tracksExpiryAndRenewalCapability() {
        AngelOneSession session = new AngelOneSession(
                "client", "jwt", "refresh", "feed", NOW, NOW.plusSeconds(120));

        assertThat(session.isExpired(NOW)).isFalse();
        assertThat(session.isExpired(NOW.plusSeconds(121))).isTrue();
        assertThat(session.isExpiring(NOW, Duration.ofSeconds(60))).isFalse();
        assertThat(session.isExpiring(NOW.plusSeconds(61), Duration.ofSeconds(60))).isTrue();
        assertThat(session.canRefresh()).isTrue();
    }

    @Test
    void toStringNeverContainsTokens() {
        AngelOneSession session = new AngelOneSession(
                "client", "access-secret", "refresh-secret", "feed-secret", NOW, NOW.plusSeconds(120));

        assertThat(session.toString())
                .doesNotContain("access-secret")
                .doesNotContain("refresh-secret")
                .doesNotContain("feed-secret")
                .contains("clientCode=***");
    }
}
