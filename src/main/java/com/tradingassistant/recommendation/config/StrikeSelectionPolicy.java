package com.tradingassistant.recommendation.config;

import com.tradingassistant.recommendation.exception.RecommendationValidationException;

import java.util.Objects;

/**
 * Configurable strike geometry. Offset and maximum distance are explicit
 * configuration values, never hardcoded contract offsets.
 *
 * @param mode          ATM, ITM or OTM
 * @param offsetSteps   number of chain strike steps away from ATM
 * @param maximumSteps  permitted absolute offset; keeps a typo bounded
 */
public record StrikeSelectionPolicy(
        StrikeMode mode,
        int offsetSteps,
        int maximumSteps) {

    public StrikeSelectionPolicy {
        mode = Objects.requireNonNull(mode, "mode must not be null");
        if (offsetSteps < 0) {
            throw new RecommendationValidationException("offsetSteps must not be negative");
        }
        if (maximumSteps < 0) {
            throw new RecommendationValidationException("maximumSteps must not be negative");
        }
        if (offsetSteps > maximumSteps) {
            throw new RecommendationValidationException(
                    "offsetSteps must not exceed maximumSteps");
        }
        if (mode == StrikeMode.ATM && offsetSteps != 0) {
            throw new RecommendationValidationException(
                    "ATM strike selection requires offsetSteps=0");
        }
        if (mode != StrikeMode.ATM && offsetSteps == 0) {
            throw new RecommendationValidationException(
                    "ITM and OTM strike selection require offsetSteps greater than zero");
        }
    }
}
