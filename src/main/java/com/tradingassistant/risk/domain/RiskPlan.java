package com.tradingassistant.risk.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable intermediate result of the risk mathematics.
 *
 * <p>The plan is derived once per assessment so every rule validates the same
 * numbers and the published assessment can be rebuilt from it. It holds only
 * calculations: the risk budget, the volatility-adjusted stop and the size that
 * budget funds.</p>
 *
 * @param capital              reference capital of the policy
 * @param riskPercent          risk percentage applied (possibly an instrument override)
 * @param riskAmount           capital multiplied by the risk percentage
 * @param atr                  average true range, empty when unavailable
 * @param volatilityAdjustment factor applied to the configured ATR multiple
 * @param atrMultiple          effective ATR multiple
 * @param stop                 derived stop distance
 * @param sizing               derived size, empty when no stop or price exists
 * @param availableReward      expected move of the option market, empty when unavailable
 * @param fractionalApplied    whether a fractional quantity was permitted
 */
public record RiskPlan(
        BigDecimal capital,
        BigDecimal riskPercent,
        BigDecimal riskAmount,
        Optional<BigDecimal> atr,
        BigDecimal volatilityAdjustment,
        BigDecimal atrMultiple,
        StopCalculation stop,
        Optional<PositionSizing> sizing,
        Optional<BigDecimal> availableReward,
        boolean fractionalApplied) {

    public RiskPlan {
        if (capital == null || capital.signum() <= 0) {
            throw new IllegalArgumentException("capital must be positive");
        }
        if (riskPercent == null || riskPercent.signum() <= 0) {
            throw new IllegalArgumentException("riskPercent must be positive");
        }
        if (riskAmount == null || riskAmount.signum() < 0) {
            throw new IllegalArgumentException("riskAmount must not be negative");
        }
        atr = atr == null ? Optional.empty() : atr;
        volatilityAdjustment = Objects.requireNonNull(volatilityAdjustment,
                "volatilityAdjustment must not be null");
        atrMultiple = Objects.requireNonNull(atrMultiple, "atrMultiple must not be null");
        stop = Objects.requireNonNull(stop, "stop must not be null");
        sizing = sizing == null ? Optional.empty() : sizing;
        availableReward = availableReward == null ? Optional.empty() : availableReward;
    }

    public boolean stopAvailable() {
        return stop.available();
    }

    public boolean sizingAvailable() {
        return sizing.isPresent() && sizing.get().available();
    }

    public BigDecimal stopDistance() {
        return stop.distance();
    }

    public BigDecimal quantity() {
        return sizing.map(PositionSizing::quantity).orElse(BigDecimal.ZERO);
    }

    public BigDecimal positionValue() {
        return sizing.map(PositionSizing::positionValue).orElse(BigDecimal.ZERO);
    }

    public BigDecimal requiredLeverage() {
        return sizing.map(PositionSizing::requiredLeverage).orElse(BigDecimal.ZERO);
    }
}
