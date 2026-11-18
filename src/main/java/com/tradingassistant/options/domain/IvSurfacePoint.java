package com.tradingassistant.options.domain;

import com.tradingassistant.market.domain.OptionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One cell of the implied-volatility surface: an expiry, a strike and an option
 * type with the solved volatility. The surface is a prepared immutable list so a
 * later phase can render or persist it without touching the pricing engine.
 */
public record IvSurfacePoint(
        LocalDate expiry,
        BigDecimal strike,
        OptionType optionType,
        BigDecimal impliedVolatility) {

    public IvSurfacePoint {
        expiry = Objects.requireNonNull(expiry, "expiry must not be null");
        strike = Objects.requireNonNull(strike, "strike must not be null");
        optionType = Objects.requireNonNull(optionType, "optionType must not be null");
        impliedVolatility = Objects.requireNonNull(impliedVolatility,
                "impliedVolatility must not be null");
        if (impliedVolatility.signum() < 0) {
            throw new IllegalArgumentException("impliedVolatility must not be negative");
        }
    }
}
