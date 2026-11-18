package com.tradingassistant.risk.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Descriptive comparison of the configured reward-to-risk floor with the reward
 * the option market currently implies.
 *
 * <p>No target price is invented: when an option assessment exists, its expected
 * move is the reward reference; otherwise the comparison is undetermined and the
 * optional values stay empty.</p>
 *
 * @param minimumRatio     configured floor
 * @param requiredReward   stop distance multiplied by the floor
 * @param availableReward  expected move of the option market
 * @param achievedRatio    available reward divided by the stop distance
 */
public record RiskRewardValidation(
        BigDecimal minimumRatio,
        Optional<BigDecimal> requiredReward,
        Optional<BigDecimal> availableReward,
        Optional<BigDecimal> achievedRatio) {

    public RiskRewardValidation {
        minimumRatio = Objects.requireNonNull(minimumRatio, "minimumRatio must not be null");
        if (minimumRatio.signum() <= 0) {
            throw new IllegalArgumentException("minimumRatio must be positive");
        }
        requiredReward = requiredReward == null ? Optional.empty() : requiredReward;
        availableReward = availableReward == null ? Optional.empty() : availableReward;
        achievedRatio = achievedRatio == null ? Optional.empty() : achievedRatio;
    }
}
