package com.tradingassistant.recommendation.domain;

import com.tradingassistant.recommendation.exception.RecommendationValidationException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable output of one independent scorer.
 *
 * @param scorerId    scorer identity
 * @param score       awarded points
 * @param maximum     maximum points this scorer can award
 * @param bias        direction this scorer supports
 * @param explanation deterministic reason for the award
 * @param diagnostics observations used to reach it
 */
public record ScorerResult(
        ScorerId scorerId,
        BigDecimal score,
        BigDecimal maximum,
        DirectionalBias bias,
        String explanation,
        Map<String, String> diagnostics) {

    public ScorerResult {
        scorerId = Objects.requireNonNull(scorerId, "scorerId must not be null");
        score = Objects.requireNonNull(score, "score must not be null");
        maximum = Objects.requireNonNull(maximum, "maximum must not be null");
        bias = Objects.requireNonNull(bias, "bias must not be null");
        if (maximum.signum() <= 0) {
            throw new RecommendationValidationException("maximum must be positive");
        }
        if (score.signum() < 0 || score.compareTo(maximum) > 0) {
            throw new RecommendationValidationException(
                    "score must be between zero and maximum");
        }
        if (explanation == null || explanation.isBlank()) {
            throw new RecommendationValidationException("explanation must not be blank");
        }
        diagnostics = diagnostics == null ? Map.of() : Map.copyOf(diagnostics);
        explanation = explanation.trim();
    }
}
