package com.tradingassistant.options.volatility;

import com.tradingassistant.market.domain.OptionType;
import com.tradingassistant.options.config.OptionsAnalyticsConfig;
import com.tradingassistant.options.greeks.BlackScholesModel;
import com.tradingassistant.options.math.DecimalMath;
import com.tradingassistant.options.math.ExpiryTime;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Solves implied volatility from a market premium with a bracketed bisection.
 *
 * <p>Bisection is used instead of Newton iteration because it cannot diverge and
 * its result is deterministic for a given configuration. The search is bounded
 * by the configured volatility range and iteration count, so a premium that no
 * volatility can reproduce yields an empty result instead of a fabricated
 * number.</p>
 */
public final class ImpliedVolatilitySolver {

    private static final BigDecimal MINIMUM_WIDTH = new BigDecimal("1E-8");

    private final OptionsAnalyticsConfig config;
    private final BlackScholesModel model;

    public ImpliedVolatilitySolver(OptionsAnalyticsConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.model = new BlackScholesModel();
    }

    public Optional<BigDecimal> solve(OptionType type, BigDecimal spot, BigDecimal strike,
                                      LocalDate expiry, Instant at, BigDecimal marketPremium) {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(spot, "spot must not be null");
        Objects.requireNonNull(strike, "strike must not be null");
        Objects.requireNonNull(expiry, "expiry must not be null");
        Objects.requireNonNull(at, "at must not be null");
        Objects.requireNonNull(marketPremium, "marketPremium must not be null");
        if (spot.signum() <= 0 || strike.signum() <= 0) {
            throw new IllegalArgumentException("spot and strike must be positive");
        }
        if (marketPremium.signum() < 0) {
            throw new IllegalArgumentException("marketPremium must not be negative");
        }
        BigDecimal years = ExpiryTime.yearsToExpiry(expiry, at, config.exchangeZone(),
                config.dayCount());
        if (years.signum() == 0 || marketPremium.signum() == 0) {
            return Optional.empty();
        }
        BigDecimal low = config.minimumVolatility();
        BigDecimal high = config.maximumVolatility();
        BigDecimal lowDifference = difference(type, spot, strike, expiry, at, low, years,
                marketPremium);
        BigDecimal highDifference = difference(type, spot, strike, expiry, at, high, years,
                marketPremium);
        if (lowDifference.signum() > 0 || highDifference.signum() < 0) {
            return Optional.empty();
        }
        for (int iteration = 0; iteration < config.volatilityIterations(); iteration++) {
            BigDecimal middle = low.add(high, DecimalMath.CONTEXT)
                    .divide(DecimalMath.TWO, DecimalMath.CONTEXT);
            BigDecimal difference = difference(type, spot, strike, expiry, at, middle, years,
                    marketPremium);
            if (difference.abs().compareTo(config.volatilityTolerance()) <= 0) {
                return Optional.of(middle);
            }
            if (difference.signum() > 0) {
                high = middle;
            } else {
                low = middle;
            }
            if (high.subtract(low).abs().compareTo(MINIMUM_WIDTH) <= 0) {
                break;
            }
        }
        return Optional.of(low.add(high, DecimalMath.CONTEXT)
                .divide(DecimalMath.TWO, DecimalMath.CONTEXT));
    }

    private BigDecimal difference(OptionType type, BigDecimal spot, BigDecimal strike,
                                  LocalDate expiry, Instant at, BigDecimal volatility,
                                  BigDecimal years, BigDecimal marketPremium) {
        BigDecimal modelled = model.price(type, spot, strike, config.riskFreeRate(),
                config.dividendYield(), volatility, years, config.dayCount());
        return modelled.subtract(marketPremium, DecimalMath.CONTEXT);
    }
}
