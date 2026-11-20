package com.tradingassistant.recommendation.domain;

import com.tradingassistant.recommendation.exception.RecommendationValidationException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;
import java.util.Objects;

/**
 * One scorer's contribution after the configured weight was applied.
 *
 * <p>The contribution is a visible arithmetic value: score divided by maximum,
 * multiplied by weight. No point in the total is unexplained.</p>
 */
public record ComponentScore(
        ScorerId scorerId,
        String label,
        BigDecimal score,
        BigDecimal maximum,
        BigDecimal weight,
        BigDecimal weightedContribution,
        DirectionalBias bias,
        String explanation,
        Map<String, String> diagnostics) {

    public ComponentScore {
        scorerId = Objects.requireNonNull(scorerId, "scorerId must not be null");
        if (label == null || label.isBlank()) {
            throw new RecommendationValidationException("label must not be blank");
        }
        score = Objects.requireNonNull(score, "score must not be null");
        maximum = Objects.requireNonNull(maximum, "maximum must not be null");
        weight = Objects.requireNonNull(weight, "weight must not be null");
        weightedContribution = Objects.requireNonNull(weightedContribution,
                "weightedContribution must not be null");
        bias = Objects.requireNonNull(bias, "bias must not be null");
        if (maximum.signum() <= 0) {
            throw new RecommendationValidationException("maximum must be positive");
        }
        if (score.signum() < 0 || score.compareTo(maximum) > 0) {
            throw new RecommendationValidationException(
                    "score must be between zero and maximum");
        }
        if (weight.signum() < 0 || weightedContribution.signum() < 0) {
            throw new RecommendationValidationException(
                    "weight and contribution must not be negative");
        }
        if (explanation == null || explanation.isBlank()) {
            throw new RecommendationValidationException("explanation must not be blank");
        }
        diagnostics = diagnostics == null ? Map.of() : Map.copyOf(diagnostics);
        explanation = explanation.trim();
        label = label.trim();
    }

    public static ComponentScore from(ScorerResult result, BigDecimal weight) {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(weight, "weight must not be null");
        BigDecimal contribution = result.score()
                .divide(result.maximum(), MathContext.DECIMAL128)
                .multiply(weight);
        return new ComponentScore(result.scorerId(), result.scorerId().label(),
                result.score(), result.maximum(), weight, contribution, result.bias(),
                result.explanation(), result.diagnostics());
    }
}
