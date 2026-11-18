package com.tradingassistant.risk.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Descriptive comparison of the planned risk with the configured daily loss
 * limit.
 *
 * <p>The limit is derived from the reference capital and is not account state:
 * nothing here reads, synchronizes or tracks a real account balance.</p>
 *
 * @param dailyLimit       maximum loss the policy allows for a day
 * @param plannedRisk      risk the computed plan would commit
 * @param utilizationPercent planned risk as a percentage of the daily limit
 */
public record DailyLossValidation(
        BigDecimal dailyLimit,
        Optional<BigDecimal> plannedRisk,
        Optional<BigDecimal> utilizationPercent) {

    public DailyLossValidation {
        dailyLimit = Objects.requireNonNull(dailyLimit, "dailyLimit must not be null");
        if (dailyLimit.signum() < 0) {
            throw new IllegalArgumentException("dailyLimit must not be negative");
        }
        plannedRisk = plannedRisk == null ? Optional.empty() : plannedRisk;
        utilizationPercent = utilizationPercent == null ? Optional.empty()
                : utilizationPercent;
    }
}
