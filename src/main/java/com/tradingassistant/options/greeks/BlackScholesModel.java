package com.tradingassistant.options.greeks;

import com.tradingassistant.market.domain.OptionType;
import com.tradingassistant.options.domain.Greeks;
import com.tradingassistant.options.math.DecimalMath;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Black-Scholes-Merton model for European options on a continuous dividend
 * yield, evaluated entirely with {@link BigDecimal} arithmetic.
 *
 * <p>The model is stateless and exposes only premium and sensitivities; no
 * intermediate term leaves this class. Theta is returned per calendar day and
 * vega and rho per one percentage point, matching the documented
 * {@link Greeks} units.</p>
 */
public final class BlackScholesModel {

    public static final String NAME = "black-scholes-merton";

    public BigDecimal price(OptionType type, BigDecimal spot, BigDecimal strike,
                            BigDecimal rate, BigDecimal dividendYield,
                            BigDecimal volatility, BigDecimal years, int dayCount) {
        Inputs inputs = validate(type, spot, strike, rate, dividendYield, volatility, years,
                dayCount);
        if (inputs.years().signum() == 0 || inputs.volatility().signum() == 0) {
            return intrinsicOrForward(inputs);
        }
        BigDecimal d1 = d1(inputs);
        BigDecimal d2 = d1.subtract(inputs.sigmaSqrtT(), DecimalMath.CONTEXT);
        BigDecimal discountedSpot = inputs.spot().multiply(inputs.discountDividend(),
                DecimalMath.CONTEXT);
        BigDecimal discountedStrike = inputs.strike().multiply(inputs.discountRate(),
                DecimalMath.CONTEXT);
        if (type.isCall()) {
            return discountedSpot.multiply(DecimalMath.normalCdf(d1), DecimalMath.CONTEXT)
                    .subtract(discountedStrike.multiply(DecimalMath.normalCdf(d2),
                            DecimalMath.CONTEXT), DecimalMath.CONTEXT);
        }
        return discountedStrike.multiply(
                        DecimalMath.normalCdf(d2.negate()), DecimalMath.CONTEXT)
                .subtract(discountedSpot.multiply(
                        DecimalMath.normalCdf(d1.negate()), DecimalMath.CONTEXT),
                        DecimalMath.CONTEXT);
    }

    public Greeks greeks(OptionType type, BigDecimal spot, BigDecimal strike,
                         BigDecimal rate, BigDecimal dividendYield,
                         BigDecimal volatility, BigDecimal years, int dayCount) {
        Inputs inputs = validate(type, spot, strike, rate, dividendYield, volatility, years,
                dayCount);
        if (inputs.years().signum() == 0 || inputs.volatility().signum() == 0) {
            return degenerateGreeks(inputs);
        }
        BigDecimal d1 = d1(inputs);
        BigDecimal d2 = d1.subtract(inputs.sigmaSqrtT(), DecimalMath.CONTEXT);
        BigDecimal pdf = DecimalMath.normalPdf(d1);
        BigDecimal cdfD1 = DecimalMath.normalCdf(d1);
        BigDecimal cdfD2 = DecimalMath.normalCdf(d2);
        BigDecimal cdfNegD1 = DecimalMath.normalCdf(d1.negate());
        BigDecimal cdfNegD2 = DecimalMath.normalCdf(d2.negate());
        BigDecimal discountDividend = inputs.discountDividend();
        BigDecimal discountRate = inputs.discountRate();

        BigDecimal delta = type.isCall()
                ? discountDividend.multiply(cdfD1, DecimalMath.CONTEXT)
                : discountDividend.multiply(cdfD1.subtract(BigDecimal.ONE), DecimalMath.CONTEXT);
        if (delta.abs().compareTo(BigDecimal.ONE.add(new BigDecimal("1E-9"))) > 0) {
            throw new IllegalStateException("delta outside [-1, 1]");
        }

        BigDecimal gamma = discountDividend.multiply(pdf, DecimalMath.CONTEXT)
                .divide(inputs.spot().multiply(inputs.sigmaSqrtT(), DecimalMath.CONTEXT),
                        DecimalMath.CONTEXT);
        BigDecimal vega = inputs.spot().multiply(discountDividend, DecimalMath.CONTEXT)
                .multiply(pdf, DecimalMath.CONTEXT)
                .multiply(inputs.sqrtT(), DecimalMath.CONTEXT)
                .divide(DecimalMath.HUNDRED, DecimalMath.CONTEXT);

        BigDecimal carry = inputs.spot().multiply(inputs.volatility(), DecimalMath.CONTEXT)
                .multiply(discountDividend, DecimalMath.CONTEXT)
                .multiply(pdf, DecimalMath.CONTEXT)
                .divide(DecimalMath.TWO.multiply(inputs.sqrtT(), DecimalMath.CONTEXT),
                        DecimalMath.CONTEXT);
        BigDecimal rateTerm = inputs.rate().multiply(inputs.strike(), DecimalMath.CONTEXT)
                .multiply(discountRate, DecimalMath.CONTEXT);
        BigDecimal yieldTerm = inputs.dividendYield().multiply(inputs.spot(),
                DecimalMath.CONTEXT).multiply(discountDividend, DecimalMath.CONTEXT);

        BigDecimal annualTheta;
        BigDecimal rho;
        if (type.isCall()) {
            annualTheta = carry.negate()
                    .subtract(rateTerm.multiply(cdfD2, DecimalMath.CONTEXT), DecimalMath.CONTEXT)
                    .add(yieldTerm.multiply(cdfD1, DecimalMath.CONTEXT), DecimalMath.CONTEXT);
            rho = inputs.strike().multiply(inputs.years(), DecimalMath.CONTEXT)
                    .multiply(discountRate, DecimalMath.CONTEXT)
                    .multiply(cdfD2, DecimalMath.CONTEXT)
                    .divide(DecimalMath.HUNDRED, DecimalMath.CONTEXT);
        } else {
            annualTheta = carry.negate()
                    .add(rateTerm.multiply(cdfNegD2, DecimalMath.CONTEXT), DecimalMath.CONTEXT)
                    .subtract(yieldTerm.multiply(cdfNegD1, DecimalMath.CONTEXT),
                            DecimalMath.CONTEXT);
            rho = inputs.strike().multiply(inputs.years(), DecimalMath.CONTEXT)
                    .multiply(discountRate, DecimalMath.CONTEXT)
                    .multiply(cdfNegD2, DecimalMath.CONTEXT)
                    .divide(DecimalMath.HUNDRED, DecimalMath.CONTEXT)
                    .negate();
        }
        BigDecimal theta = annualTheta.divide(BigDecimal.valueOf(dayCount), DecimalMath.CONTEXT);
        return new Greeks(delta, gamma, theta, vega, rho);
    }

    private static BigDecimal d1(Inputs inputs) {
        BigDecimal logMoneyness = DecimalMath.ln(
                inputs.spot().divide(inputs.strike(), DecimalMath.CONTEXT));
        BigDecimal drift = inputs.rate().subtract(inputs.dividendYield(), DecimalMath.CONTEXT)
                .add(inputs.volatility().multiply(inputs.volatility(), DecimalMath.CONTEXT)
                        .divide(DecimalMath.TWO, DecimalMath.CONTEXT), DecimalMath.CONTEXT)
                .multiply(inputs.years(), DecimalMath.CONTEXT);
        return logMoneyness.add(drift, DecimalMath.CONTEXT)
                .divide(inputs.sigmaSqrtT(), DecimalMath.CONTEXT);
    }

    private static BigDecimal intrinsicOrForward(Inputs inputs) {
        BigDecimal discountedSpot = inputs.spot().multiply(inputs.discountDividend(),
                DecimalMath.CONTEXT);
        BigDecimal discountedStrike = inputs.strike().multiply(inputs.discountRate(),
                DecimalMath.CONTEXT);
        BigDecimal difference = inputs.volatility().signum() == 0 && inputs.years().signum() > 0
                ? (inputs.type().isCall()
                        ? discountedSpot.subtract(discountedStrike, DecimalMath.CONTEXT)
                        : discountedStrike.subtract(discountedSpot, DecimalMath.CONTEXT))
                : (inputs.type().isCall()
                        ? inputs.spot().subtract(inputs.strike(), DecimalMath.CONTEXT)
                        : inputs.strike().subtract(inputs.spot(), DecimalMath.CONTEXT));
        return difference.max(BigDecimal.ZERO);
    }

    private static Greeks degenerateGreeks(Inputs inputs) {
        boolean call = inputs.type().isCall();
        int direction = call ? 1 : -1;
        BigDecimal delta;
        if (inputs.years().signum() == 0) {
            boolean inTheMoney = call
                    ? inputs.spot().compareTo(inputs.strike()) > 0
                    : inputs.spot().compareTo(inputs.strike()) < 0;
            delta = inTheMoney ? BigDecimal.valueOf(direction) : BigDecimal.ZERO;
        } else {
            BigDecimal discountedSpot = inputs.spot().multiply(inputs.discountDividend(),
                    DecimalMath.CONTEXT);
            BigDecimal discountedStrike = inputs.strike().multiply(inputs.discountRate(),
                    DecimalMath.CONTEXT);
            boolean inTheMoney = call
                    ? discountedSpot.compareTo(discountedStrike) > 0
                    : discountedStrike.compareTo(discountedSpot) > 0;
            delta = inTheMoney ? BigDecimal.valueOf(direction) : BigDecimal.ZERO;
        }
        return new Greeks(delta, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO);
    }

    private static Inputs validate(OptionType type, BigDecimal spot, BigDecimal strike,
                                   BigDecimal rate, BigDecimal dividendYield,
                                   BigDecimal volatility, BigDecimal years, int dayCount) {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(spot, "spot must not be null");
        Objects.requireNonNull(strike, "strike must not be null");
        Objects.requireNonNull(rate, "rate must not be null");
        Objects.requireNonNull(dividendYield, "dividendYield must not be null");
        Objects.requireNonNull(volatility, "volatility must not be null");
        Objects.requireNonNull(years, "years must not be null");
        if (spot.signum() <= 0) {
            throw new IllegalArgumentException("spot must be positive");
        }
        if (strike.signum() <= 0) {
            throw new IllegalArgumentException("strike must be positive");
        }
        if (volatility.signum() < 0) {
            throw new IllegalArgumentException("volatility must not be negative");
        }
        if (years.signum() < 0) {
            throw new IllegalArgumentException("years must not be negative");
        }
        if (dayCount < 1) {
            throw new IllegalArgumentException("dayCount must be positive");
        }
        BigDecimal sqrtT = DecimalMath.sqrt(years);
        BigDecimal sigmaSqrtT = volatility.multiply(sqrtT, DecimalMath.CONTEXT);
        return new Inputs(type, spot, strike, rate, dividendYield, volatility, years, dayCount,
                sqrtT, sigmaSqrtT,
                DecimalMath.exp(dividendYield.multiply(years, DecimalMath.CONTEXT).negate()),
                DecimalMath.exp(rate.multiply(years, DecimalMath.CONTEXT).negate()));
    }

    private record Inputs(
            OptionType type,
            BigDecimal spot,
            BigDecimal strike,
            BigDecimal rate,
            BigDecimal dividendYield,
            BigDecimal volatility,
            BigDecimal years,
            int dayCount,
            BigDecimal sqrtT,
            BigDecimal sigmaSqrtT,
            BigDecimal discountDividend,
            BigDecimal discountRate) {
    }
}
