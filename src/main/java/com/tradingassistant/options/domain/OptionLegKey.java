package com.tradingassistant.options.domain;

import com.tradingassistant.market.domain.OptionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Identity of one leg across snapshots: expiry, canonical strike and type.
 */
public record OptionLegKey(
        LocalDate expiry,
        BigDecimal strike,
        OptionType optionType) {

    public OptionLegKey {
        expiry = Objects.requireNonNull(expiry, "expiry must not be null");
        strike = Objects.requireNonNull(strike, "strike must not be null");
        if (strike.signum() <= 0) {
            throw new IllegalArgumentException("strike must be positive");
        }
        optionType = Objects.requireNonNull(optionType, "optionType must not be null");
        strike = canonical(strike);
    }

    public static OptionLegKey of(Strike leg) {
        return new OptionLegKey(leg.expiry(), leg.strike(), leg.optionType());
    }

    private static BigDecimal canonical(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
