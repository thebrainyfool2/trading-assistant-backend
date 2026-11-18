package com.tradingassistant.options.domain;

import com.tradingassistant.intelligence.domain.AssessmentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Expected move of the underlying over the expiry horizon and over daily and
 * weekly horizons, expressed as absolute price distances.
 */
public record ExpectedMoveSummary(
        ExpectedMoveMethod method,
        Optional<LocalDate> expiry,
        Optional<BigDecimal> expiryMove,
        Optional<BigDecimal> dailyMove,
        Optional<BigDecimal> weeklyMove,
        Optional<BigDecimal> upperBound,
        Optional<BigDecimal> lowerBound,
        AssessmentStatus status,
        String summary) {

    public ExpectedMoveSummary {
        method = Objects.requireNonNull(method, "method must not be null");
        expiry = expiry == null ? Optional.empty() : expiry;
        expiryMove = expiryMove == null ? Optional.empty() : expiryMove;
        dailyMove = dailyMove == null ? Optional.empty() : dailyMove;
        weeklyMove = weeklyMove == null ? Optional.empty() : weeklyMove;
        upperBound = upperBound == null ? Optional.empty() : upperBound;
        lowerBound = lowerBound == null ? Optional.empty() : lowerBound;
        status = Objects.requireNonNull(status, "status must not be null");
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        summary = summary.trim();
    }
}
