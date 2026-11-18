package com.tradingassistant.options.greeks;

import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.market.domain.OptionType;
import com.tradingassistant.options.config.OptionsAnalyticsConfig;
import com.tradingassistant.options.domain.Greeks;
import com.tradingassistant.options.domain.GreeksAssessment;
import com.tradingassistant.options.exception.OptionsValidationException;
import com.tradingassistant.options.math.ExpiryTime;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Evaluates European option sensitivities for a chain leg.
 *
 * <p>The engine owns configuration and model selection only; it holds no state.
 * Volatility outside the configured range, a non-positive spot or strike, and a
 * missing input are rejected deterministically rather than producing a partial
 * Greek.</p>
 */
public final class GreeksEngine {

    private final OptionsAnalyticsConfig config;
    private final BlackScholesModel model;

    public GreeksEngine(OptionsAnalyticsConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.model = new BlackScholesModel();
    }

    public String modelName() {
        return BlackScholesModel.NAME;
    }

    /**
     * Calendar time between the capture instant and the expiry, in years.
     */
    public BigDecimal timeToExpiry(LocalDate expiry, Instant at) {
        return ExpiryTime.yearsToExpiry(expiry, at, config.exchangeZone(), config.dayCount());
    }

    public GreeksAssessment evaluate(OptionType type, BigDecimal spot, BigDecimal strike,
                                     LocalDate expiry, Instant at, BigDecimal volatility) {
        Objects.requireNonNull(expiry, "expiry must not be null");
        Objects.requireNonNull(at, "at must not be null");
        BigDecimal years = timeToExpiry(expiry, at);
        if (years.signum() == 0) {
            return new GreeksAssessment(Optional.empty(), AssessmentStatus.UNKNOWN,
                    modelName(), "contract expired before the capture instant");
        }
        BigDecimal validatedVolatility = validateVolatility(volatility);
        Greeks greeks = model.greeks(type, spot, strike, config.riskFreeRate(),
                config.dividendYield(), validatedVolatility, years, config.dayCount());
        validateGreeks(type, greeks);
        return new GreeksAssessment(Optional.of(greeks), AssessmentStatus.READY, modelName(),
                "European model with " + config.dayCount() + " day-count basis");
    }

    /**
     * Model premium for the given inputs, used by the implied-volatility solver.
     */
    public BigDecimal price(OptionType type, BigDecimal spot, BigDecimal strike,
                            LocalDate expiry, Instant at, BigDecimal volatility) {
        Objects.requireNonNull(expiry, "expiry must not be null");
        Objects.requireNonNull(at, "at must not be null");
        BigDecimal years = timeToExpiry(expiry, at);
        BigDecimal validatedVolatility = validateVolatility(volatility);
        return model.price(type, spot, strike, config.riskFreeRate(), config.dividendYield(),
                validatedVolatility, years, config.dayCount());
    }

    /**
     * Rejects a model result that cannot describe a single European leg. Scaled
     * aggregates are validated by their consumers, not here.
     */
    private void validateGreeks(OptionType type, Greeks greeks) {
        boolean deltaInRange = type.isCall()
                ? greeks.delta().signum() >= 0 && greeks.delta().compareTo(BigDecimal.ONE) <= 0
                : greeks.delta().signum() <= 0
                        && greeks.delta().compareTo(BigDecimal.ONE.negate()) >= 0;
        if (!deltaInRange || greeks.gamma().signum() < 0 || greeks.vega().signum() < 0) {
            throw new OptionsValidationException("model produced invalid Greeks");
        }
    }

    private BigDecimal validateVolatility(BigDecimal volatility) {
        Objects.requireNonNull(volatility, "volatility must not be null");
        if (volatility.signum() <= 0) {
            throw new OptionsValidationException("implied volatility must be positive");
        }
        if (volatility.compareTo(config.minimumVolatility()) < 0
                || volatility.compareTo(config.maximumVolatility()) > 0) {
            throw new OptionsValidationException(
                    "implied volatility is outside the configured range");
        }
        return volatility;
    }
}
