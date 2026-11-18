package com.tradingassistant.broker.angelone.auth;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtExpiryParserTest {

    private final JwtExpiryParser parser = new JwtExpiryParser(new ObjectMapper());

    @Test
    void readsExpiryFromJwtPayload() {
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"exp\":1700000000}".getBytes(StandardCharsets.UTF_8));

        assertThat(parser.expiresAt("header." + payload + ".signature"))
                .contains(Instant.ofEpochSecond(1700000000L));
    }

    @Test
    void returnsEmptyWhenExpiryIsAbsent() {
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"client\"}".getBytes(StandardCharsets.UTF_8));

        assertThat(parser.expiresAt("header." + payload + ".signature")).isEmpty();
    }

    @Test
    void returnsEmptyForMalformedTokens() {
        assertThat(parser.expiresAt("not-a-jwt")).isEmpty();
        assertThat(parser.expiresAt(null)).isEmpty();
    }
}
