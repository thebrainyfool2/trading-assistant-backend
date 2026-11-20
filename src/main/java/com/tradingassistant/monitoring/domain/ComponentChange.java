package com.tradingassistant.monitoring.domain;

import com.tradingassistant.recommendation.domain.ScorerId;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Change in one Phase 14 scorer's normalized score between two monitoring
 * evaluations. Positive deltas are improvement, negative deltas deterioration.
 */
public record ComponentChange(
        ScorerId scorerId,
        BigDecimal previousScore,
        BigDecimal currentScore,
        BigDecimal delta,
        String explanation) {

    public ComponentChange {
        scorerId = Objects.requireNonNull(scorerId, "scorerId must not be null");
        previousScore = Objects.requireNonNull(previousScore,
                "previousScore must not be null");
        currentScore = Objects.requireNonNull(currentScore, "currentScore must not be null");
        delta = Objects.requireNonNull(delta, "delta must not be null");
        if (previousScore.signum() < 0 || currentScore.signum() < 0) {
            throw new IllegalArgumentException("component scores must not be negative");
        }
        if (previousScore.compareTo(BigDecimal.valueOf(100)) > 0
                || currentScore.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("component scores must not exceed 100");
        }
        if (delta.compareTo(currentScore.subtract(previousScore)) != 0) {
            throw new IllegalArgumentException("delta must equal currentScore - previousScore");
        }
        if (explanation == null || explanation.isBlank()) {
            throw new IllegalArgumentException("explanation must not be blank");
        }
        explanation = explanation.trim();
    }
}