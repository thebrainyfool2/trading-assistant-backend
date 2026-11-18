package com.tradingassistant.options.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable option sensitivities for one contract.
 *
 * <p>Units are fixed so two assessments are comparable:</p>
 * <ul>
 *   <li>{@code delta} — change in premium per one unit of underlying</li>
 *   <li>{@code gamma} — change in delta per one unit of underlying</li>
 *   <li>{@code theta} — premium change per calendar day</li>
 *   <li>{@code vega} — premium change per one percentage point of volatility</li>
 *   <li>{@code rho} — premium change per one percentage point of interest rate</li>
 * </ul>
 */
public record Greeks(
        BigDecimal delta,
        BigDecimal gamma,
        BigDecimal theta,
        BigDecimal vega,
        BigDecimal rho) {

    public Greeks {
        delta = Objects.requireNonNull(delta, "delta must not be null");
        gamma = Objects.requireNonNull(gamma, "gamma must not be null");
        theta = Objects.requireNonNull(theta, "theta must not be null");
        vega = Objects.requireNonNull(vega, "vega must not be null");
        rho = Objects.requireNonNull(rho, "rho must not be null");
    }

    public static Greeks zero() {
        return new Greeks(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public Greeks add(Greeks other) {
        return new Greeks(delta.add(other.delta), gamma.add(other.gamma),
                theta.add(other.theta), vega.add(other.vega), rho.add(other.rho));
    }

    public Greeks scale(BigDecimal factor) {
        return new Greeks(delta.multiply(factor), gamma.multiply(factor),
                theta.multiply(factor), vega.multiply(factor), rho.multiply(factor));
    }
}
