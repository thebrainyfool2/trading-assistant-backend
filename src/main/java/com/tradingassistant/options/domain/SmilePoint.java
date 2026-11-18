package com.tradingassistant.options.domain;

import com.tradingassistant.market.domain.OptionType;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One implied-volatility observation of the smile of an expiry.
 */
public record SmilePoint(
        BigDecimal strike,
        OptionType optionType,
        Moneyness moneyness,
        BigDecimal impliedVolatility) {

    public SmilePoint {
        strike = Objects.requireNonNull(strike, "strike must not be null");
        optionType = Objects.requireNonNull(optionType, "optionType must not be null");
        moneyness = Objects.requireNonNull(moneyness, "moneyness must not be null");
        impliedVolatility = Objects.requireNonNull(impliedVolatility,
                "impliedVolatility must not be null");
        if (impliedVolatility.signum() < 0) {
            throw new IllegalArgumentException("impliedVolatility must not be negative");
        }
    }
}
