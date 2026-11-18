package com.tradingassistant.risk.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable description of the protective stop distance.
 *
 * <p>The distance is a descriptive boundary derived from volatility. It is not an
 * order, not a target and not a recommendation to exit.</p>
 *
 * @param methodology          how the distance was derived
 * @param atr                  average true range used, zero when unavailable
 * @param atrMultiple          effective multiple after the volatility adjustment
 * @param volatilityAdjustment factor applied to the configured multiple
 * @param distance             absolute price distance, zero when unavailable
 * @param distancePercent      distance as a percentage of the reference price
 */
public record StopCalculation(
        StopMethodology methodology,
        BigDecimal atr,
        BigDecimal atrMultiple,
        BigDecimal volatilityAdjustment,
        BigDecimal distance,
        BigDecimal distancePercent) {

    public StopCalculation {
        methodology = Objects.requireNonNull(methodology, "methodology must not be null");
        atr = RiskValue.requireNonNegative(atr, "atr");
        atrMultiple = RiskValue.requireNonNegative(atrMultiple, "atrMultiple");
        volatilityAdjustment = RiskValue.requireNonNegative(volatilityAdjustment,
                "volatilityAdjustment");
        distance = RiskValue.requireNonNegative(distance, "distance");
        distancePercent = RiskValue.requireNonNegative(distancePercent, "distancePercent");
    }

    /**
     * A stop that could not be derived.
     */
    public static StopCalculation none() {
        return new StopCalculation(StopMethodology.NONE, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public boolean available() {
        return methodology != StopMethodology.NONE;
    }
}
