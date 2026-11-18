package com.tradingassistant.options.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * State of one leg at a previous capture, used to describe change without
 * retaining mutable feed objects.
 *
 * @param openInterest     open interest in contracts
 * @param volume           traded volume in contracts
 * @param premium          last traded premium
 * @param impliedVolatility implied volatility, empty when it could not be solved
 */
public record LegState(
        long openInterest,
        long volume,
        BigDecimal premium,
        Optional<BigDecimal> impliedVolatility) {

    public LegState {
        if (openInterest < 0) {
            throw new IllegalArgumentException("openInterest must not be negative");
        }
        if (volume < 0) {
            throw new IllegalArgumentException("volume must not be negative");
        }
        premium = Objects.requireNonNull(premium, "premium must not be null");
        impliedVolatility = impliedVolatility == null ? Optional.empty() : impliedVolatility;
    }
}
