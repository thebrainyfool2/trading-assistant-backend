package com.tradingassistant.recommendation.config;

import com.tradingassistant.options.domain.ExpiryKind;
import com.tradingassistant.recommendation.domain.ScorerId;
import com.tradingassistant.recommendation.exception.RecommendationValidationException;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * External configuration for the recommendation engine.
 *
 * <p>Weights are supplied by name, so an operator can tune a policy without a
 * code change. The properties are converted once into the validated immutable
 * {@link RecommendationConfig}.</p>
 */
@ConfigurationProperties(prefix = "app.recommendation")
public record RecommendationProperties(
        boolean enabled,
        Map<String, BigDecimal> weights,
        BigDecimal minimumConfidence,
        BigDecimal minimumDirectionalEdge,
        BigDecimal pcrBullishThreshold,
        BigDecimal pcrBearishThreshold,
        BigDecimal highIvRank,
        BigDecimal lowIvRank,
        List<String> allowedExpiryKinds,
        String expirySelection,
        String strikeMode,
        Integer strikeOffsetSteps,
        Integer maximumStrikeSteps) {

    public RecommendationConfig toConfig() {
        Map<ScorerId, BigDecimal> mapped = new EnumMap<>(ScorerId.class);
        if (weights != null) {
            weights.forEach((key, value) -> {
                ScorerId id = ScorerId.fromId(key).orElseThrow(() ->
                        new RecommendationValidationException(
                                "unknown scorer weight: " + key));
                if (mapped.putIfAbsent(id, value) != null) {
                    throw new RecommendationValidationException(
                            "duplicate scorer weight: " + key);
                }
            });
        }
        Set<ExpiryKind> expiries = new LinkedHashSet<>();
        if (allowedExpiryKinds != null) {
            for (String value : allowedExpiryKinds) {
                if (value == null || value.isBlank()) {
                    continue;
                }
                try {
                    expiries.add(ExpiryKind.valueOf(value.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException failure) {
                    throw new RecommendationValidationException(
                            "unknown allowed expiry kind: " + value, failure);
                }
            }
        }
        return new RecommendationConfig(enabled, mapped,
                required(minimumConfidence, "minimumConfidence"),
                required(minimumDirectionalEdge, "minimumDirectionalEdge"),
                required(pcrBullishThreshold, "pcrBullishThreshold"),
                required(pcrBearishThreshold, "pcrBearishThreshold"),
                required(highIvRank, "highIvRank"),
                required(lowIvRank, "lowIvRank"),
                expiries,
                enumValue(ExpirySelection.class, expirySelection, "expirySelection"),
                new StrikeSelectionPolicy(
                        enumValue(StrikeMode.class, strikeMode, "strikeMode"),
                        required(strikeOffsetSteps, "strikeOffsetSteps"),
                        required(maximumStrikeSteps, "maximumStrikeSteps")));
    }

    private static BigDecimal required(BigDecimal value, String field) {
        if (value == null) {
            throw new RecommendationValidationException(
                    "app.recommendation." + field + " must be configured");
        }
        return value;
    }

    private static int required(Integer value, String field) {
        if (value == null) {
            throw new RecommendationValidationException(
                    "app.recommendation." + field + " must be configured");
        }
        return value;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value,
                                                   String field) {
        if (value == null || value.isBlank()) {
            throw new RecommendationValidationException(
                    "app.recommendation." + field + " must be configured");
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new RecommendationValidationException(
                    "unknown app.recommendation." + field + ": " + value, failure);
        }
    }
}
