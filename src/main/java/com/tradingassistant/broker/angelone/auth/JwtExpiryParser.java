package com.tradingassistant.broker.angelone.auth;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Extracts the {@code exp} claim from a broker JWT for local expiry tracking.
 * Signature verification is the broker's responsibility; this parser only reads
 * the already-issued token and deliberately never logs it.
 */
public final class JwtExpiryParser {

    private final ObjectMapper objectMapper;

    public JwtExpiryParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<Instant> expiresAt(String jwt) {
        if (jwt == null || jwt.isBlank()) {
            return Optional.empty();
        }
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            return Optional.empty();
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode root = objectMapper.readTree(payload);
            JsonNode exp = root.get("exp");
            if (exp == null || !exp.canConvertToLong() || exp.asLong() <= 0) {
                return Optional.empty();
            }
            return Optional.of(Instant.ofEpochSecond(exp.asLong()));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }
}
