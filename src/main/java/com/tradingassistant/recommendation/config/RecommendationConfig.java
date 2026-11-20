package com.tradingassistant.recommendation.config;

import com.tradingassistant.options.domain.ExpiryKind;
import com.tradingassistant.recommendation.domain.ScorerId;
import com.tradingassistant.recommendation.exception.RecommendationValidationException;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable recommendation policy.
 *
 * <p>Weights are relative and are never assumed to total one hundred. The engine
 * normalises by the configured total, so changing a weight changes the result
 * without a second hardcoded total to keep in sync.</p>
 *
 * @param enabled                       whether the live service evaluates events
 * @param weights                       one non-negative weight per scorer
 * @param minimumConfidence             minimum normalised weighted confidence
 * @param minimumDirectionalEdge        minimum weighted bull/bear difference
 * @param pcrBullishThreshold           put/call OI ratio treated as bullish
 * @param pcrBearishThreshold           put/call OI ratio treated as bearish
 * @param highIvRank                    IV rank at or above which premium is expensive
 * @param lowIvRank                     IV rank at or below which premium is inexpensive
 * @param allowedExpiryKinds            weekly/monthly expiries that may be selected
 * @param expirySelection               nearest, next or far allowed expiry
 * @param strikeSelection               relative strike and configured offset
 */
public record RecommendationConfig(
        boolean enabled,
        Map<ScorerId, BigDecimal> weights,
        BigDecimal minimumConfidence,
        BigDecimal minimumDirectionalEdge,
        BigDecimal pcrBullishThreshold,
        BigDecimal pcrBearishThreshold,
        BigDecimal highIvRank,
        BigDecimal lowIvRank,
        Set<ExpiryKind> allowedExpiryKinds,
        ExpirySelection expirySelection,
        StrikeSelectionPolicy strikeSelection) {

    public RecommendationConfig {
        if (weights == null || weights.isEmpty()) {
            throw new RecommendationValidationException("weights must not be empty");
        }
        EnumMap<ScorerId, BigDecimal> checked = new EnumMap<>(ScorerId.class);
        for (ScorerId scorerId : ScorerId.values()) {
            BigDecimal value = weights.get(scorerId);
            if (value == null) {
                throw new RecommendationValidationException(
                        "missing required scorer weight: " + scorerId.id());
            }
            if (value.signum() < 0) {
                throw new RecommendationValidationException(
                        "negative weight for scorer: " + scorerId.id());
            }
            checked.put(scorerId, value.stripTrailingZeros());
        }
        weights = Map.copyOf(checked);

        minimumConfidence = percent(minimumConfidence, "minimumConfidence");
        minimumDirectionalEdge = nonNegative(minimumDirectionalEdge,
                "minimumDirectionalEdge");
        pcrBullishThreshold = positive(pcrBullishThreshold, "pcrBullishThreshold");
        pcrBearishThreshold = positive(pcrBearishThreshold, "pcrBearishThreshold");
        if (pcrBullishThreshold.compareTo(pcrBearishThreshold) <= 0) {
            throw new RecommendationValidationException(
                    "pcrBullishThreshold must be greater than pcrBearishThreshold");
        }
        highIvRank = percent(highIvRank, "highIvRank");
        lowIvRank = percent(lowIvRank, "lowIvRank");
        if (highIvRank.compareTo(lowIvRank) <= 0) {
            throw new RecommendationValidationException(
                    "highIvRank must be greater than lowIvRank");
        }
        if (allowedExpiryKinds == null || allowedExpiryKinds.isEmpty()) {
            throw new RecommendationValidationException(
                    "allowedExpiryKinds must not be empty");
        }
        allowedExpiryKinds = Set.copyOf(EnumSet.copyOf(allowedExpiryKinds));
        expirySelection = Objects.requireNonNull(expirySelection,
                "expirySelection must not be null");
        strikeSelection = Objects.requireNonNull(strikeSelection,
                "strikeSelection must not be null");

        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : weights.values()) {
            total = total.add(value);
        }
        if (total.signum() <= 0) {
            throw new RecommendationValidationException(
                    "at least one scorer weight must be greater than zero");
        }
    }

    public BigDecimal weight(ScorerId scorerId) {
        return weights.get(scorerId);
    }

    public BigDecimal totalWeight() {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : weights.values()) {
            total = total.add(value);
        }
        return total;
    }

    private static BigDecimal percent(BigDecimal value, String field) {
        if (value == null) {
            throw new RecommendationValidationException(field + " must not be null");
        }
        if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new RecommendationValidationException(
                    field + " must be between 0 and 100");
        }
        return value;
    }

    private static BigDecimal nonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new RecommendationValidationException(
                    field + " must not be negative");
        }
        return value;
    }

    private static BigDecimal positive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new RecommendationValidationException(
                    field + " must be greater than zero");
        }
        return value;
    }
}
