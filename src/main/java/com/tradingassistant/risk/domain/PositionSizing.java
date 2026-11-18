package com.tradingassistant.risk.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable result of sizing a position from a risk budget and a stop distance.
 *
 * <p>Every field is a calculation. There is no order, no fill, no recommendation
 * to trade and no capital allocation: the record states what quantity the
 * configured budget would fund at the computed stop distance, and what exposure
 * that implies.</p>
 *
 * @param quantity            units or contracts the budget funds
 * @param positionValue       quantity multiplied by the reference price
 * @param requiredLeverage    position value divided by the reference capital
 * @param referencePrice      price used for the notional calculation
 * @param fractional          whether a fractional quantity is permitted
 */
public record PositionSizing(
        BigDecimal quantity,
        BigDecimal positionValue,
        BigDecimal requiredLeverage,
        BigDecimal referencePrice,
        boolean fractional) {

    public PositionSizing {
        quantity = RiskValue.requireNonNegative(quantity, "quantity");
        positionValue = RiskValue.requireNonNegative(positionValue, "positionValue");
        requiredLeverage = RiskValue.requireNonNegative(requiredLeverage, "requiredLeverage");
        referencePrice = RiskValue.requireNonNegative(referencePrice, "referencePrice");
    }

    /**
     * A size that could not be derived.
     */
    public static PositionSizing none() {
        return new PositionSizing(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, false);
    }

    public boolean available() {
        return quantity.signum() > 0;
    }

    public boolean exceedsLeverage(BigDecimal maximumLeverage) {
        Objects.requireNonNull(maximumLeverage, "maximumLeverage must not be null");
        return requiredLeverage.compareTo(maximumLeverage) > 0;
    }
}
