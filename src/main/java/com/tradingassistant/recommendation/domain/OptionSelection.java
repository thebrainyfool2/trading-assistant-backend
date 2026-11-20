package com.tradingassistant.recommendation.domain;

import com.tradingassistant.market.domain.OptionType;
import com.tradingassistant.options.domain.ExpiryKind;
import com.tradingassistant.options.domain.Moneyness;
import com.tradingassistant.recommendation.config.StrikeMode;
import com.tradingassistant.recommendation.exception.RecommendationValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Deterministic contract choice. It deliberately contains no entry price,
 * quantity, target, stop or exit.
 */
public record OptionSelection(
        LocalDate expiry,
        ExpiryKind expiryKind,
        BigDecimal strike,
        OptionType optionType,
        Moneyness moneyness,
        StrikeMode requestedMode,
        int offsetSteps) {

    public OptionSelection {
        expiry = Objects.requireNonNull(expiry, "expiry must not be null");
        expiryKind = Objects.requireNonNull(expiryKind, "expiryKind must not be null");
        strike = Objects.requireNonNull(strike, "strike must not be null");
        optionType = Objects.requireNonNull(optionType, "optionType must not be null");
        moneyness = Objects.requireNonNull(moneyness, "moneyness must not be null");
        requestedMode = Objects.requireNonNull(requestedMode,
                "requestedMode must not be null");
        if (strike.signum() <= 0) {
            throw new RecommendationValidationException("strike must be positive");
        }
        if (offsetSteps < 0) {
            throw new RecommendationValidationException("offsetSteps must not be negative");
        }
    }
}
