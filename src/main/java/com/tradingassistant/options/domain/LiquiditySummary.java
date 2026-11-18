package com.tradingassistant.options.domain;

import com.tradingassistant.intelligence.domain.AssessmentStatus;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Description of the quoted market quality of the assessed legs.
 *
 * @param assessedLegs        legs with a two-sided quote used in the assessment
 * @param wideSpreadLegs      legs whose relative spread exceeded the configured maximum
 * @param thinLegs            legs below the configured open-interest or volume floor
 * @param averageSpreadPercent mean relative spread of the assessed legs
 * @param state               aggregate liquidity state
 * @param tradability         descriptive tradability band derived from the state
 */
public record LiquiditySummary(
        int assessedLegs,
        int wideSpreadLegs,
        int thinLegs,
        Optional<BigDecimal> averageSpreadPercent,
        LiquidityState state,
        Tradability tradability,
        AssessmentStatus status,
        String summary) {

    public LiquiditySummary {
        if (assessedLegs < 0 || wideSpreadLegs < 0 || thinLegs < 0) {
            throw new IllegalArgumentException("liquidity counts must not be negative");
        }
        if (wideSpreadLegs > assessedLegs || thinLegs > assessedLegs) {
            throw new IllegalArgumentException("liquidity counts must not exceed the assessed legs");
        }
        averageSpreadPercent = averageSpreadPercent == null ? Optional.empty()
                : averageSpreadPercent;
        state = Objects.requireNonNull(state, "state must not be null");
        tradability = Objects.requireNonNull(tradability, "tradability must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        summary = summary.trim();
    }
}
