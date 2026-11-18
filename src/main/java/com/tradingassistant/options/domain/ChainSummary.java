package com.tradingassistant.options.domain;

import com.tradingassistant.intelligence.domain.AssessmentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Structural description of the assessed chain.
 */
public record ChainSummary(
        Optional<LocalDate> nearestExpiry,
        int expiryCount,
        int strikeCount,
        int legCount,
        BigDecimal spotPrice,
        AssessmentStatus status,
        String summary) {

    public ChainSummary {
        nearestExpiry = nearestExpiry == null ? Optional.empty() : nearestExpiry;
        if (expiryCount < 0 || strikeCount < 0 || legCount < 0) {
            throw new IllegalArgumentException("chain counts must not be negative");
        }
        spotPrice = Objects.requireNonNull(spotPrice, "spotPrice must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        summary = summary.trim();
    }
}
