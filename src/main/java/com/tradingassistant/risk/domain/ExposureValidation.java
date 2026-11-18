package com.tradingassistant.risk.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Descriptive comparison of the computed position value with the configured open
 * exposure limit for one instrument.
 *
 * <p>This is a boundary calculation, not portfolio exposure management: no open
 * positions are read, tracked or aggregated here.</p>
 *
 * @param maximumExposure     maximum open exposure the policy allows
 * @param plannedExposure     position value the computed plan would imply
 * @param utilizationPercent  planned exposure as a percentage of the maximum
 */
public record ExposureValidation(
        BigDecimal maximumExposure,
        Optional<BigDecimal> plannedExposure,
        Optional<BigDecimal> utilizationPercent) {

    public ExposureValidation {
        maximumExposure = Objects.requireNonNull(maximumExposure,
                "maximumExposure must not be null");
        if (maximumExposure.signum() < 0) {
            throw new IllegalArgumentException("maximumExposure must not be negative");
        }
        plannedExposure = plannedExposure == null ? Optional.empty() : plannedExposure;
        utilizationPercent = utilizationPercent == null ? Optional.empty()
                : utilizationPercent;
    }
}
