package com.tradingassistant.options.math;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Deterministic decimal elementary functions used by the option analytics.
 *
 * <p>{@link BigDecimal} supplies no exponential, logarithm or normal
 * distribution, so this class implements them with series expansions in
 * DECIMAL128 context. The implementations are deterministic and independent of
 * platform maths libraries. The normal CDF uses the Abramowitz and Stegun
 * 26.2.17 rational approximation, whose absolute accuracy is about 1.5e-7; that
 * bound is the documented precision boundary of every Greek and probability
 * produced by this package.</p>
 */
public final class DecimalMath {

    /** Working precision for every intermediate operation. */
    public static final MathContext CONTEXT = MathContext.DECIMAL128;

    public static final BigDecimal TWO = BigDecimal.valueOf(2);
    public static final BigDecimal HALF = new BigDecimal("0.5");
    public static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private static final BigDecimal LN_TWO =
            new BigDecimal("0.6931471805599453094172321214581766");
    private static final BigDecimal SQRT_TWO_PI =
            new BigDecimal("2.5066282746310005024157652848110453");
    private static final BigDecimal ONE_POINT_FIVE = new BigDecimal("1.5");
    private static final BigDecimal SERIES_TOLERANCE = new BigDecimal("1E-30");
    private static final int SQUARING_STEPS = 12;
    private static final BigDecimal SQUARING_FACTOR =
            BigDecimal.valueOf(1L << SQUARING_STEPS);

    private static final BigDecimal AS_P = new BigDecimal("0.2316419");
    private static final BigDecimal AS_B1 = new BigDecimal("0.319381530");
    private static final BigDecimal AS_B2 = new BigDecimal("-0.356563782");
    private static final BigDecimal AS_B3 = new BigDecimal("1.781477937");
    private static final BigDecimal AS_B4 = new BigDecimal("-1.821255978");
    private static final BigDecimal AS_B5 = new BigDecimal("1.330274429");

    private DecimalMath() {
    }

    /**
     * Euler's number raised to the given power.
     *
     * @throws IllegalArgumentException when the exponent is outside the
     *                                  representable range
     */
    public static BigDecimal exp(BigDecimal exponent) {
        if (exponent == null) {
            throw new IllegalArgumentException("exponent must not be null");
        }
        if (exponent.signum() == 0) {
            return BigDecimal.ONE;
        }
        if (exponent.abs().compareTo(BigDecimal.valueOf(700)) > 0) {
            throw new IllegalArgumentException("exponent out of supported range");
        }
        BigDecimal scaled = exponent.divide(SQUARING_FACTOR, CONTEXT);
        BigDecimal sum = BigDecimal.ONE;
        BigDecimal term = BigDecimal.ONE;
        for (int n = 1; n <= 60; n++) {
            term = term.multiply(scaled, CONTEXT)
                    .divide(BigDecimal.valueOf(n), CONTEXT);
            sum = sum.add(term, CONTEXT);
            if (term.abs().compareTo(SERIES_TOLERANCE) < 0) {
                break;
            }
        }
        for (int step = 0; step < SQUARING_STEPS; step++) {
            sum = sum.multiply(sum, CONTEXT);
        }
        return sum;
    }

    /**
     * Natural logarithm of a positive value.
     *
     * @throws IllegalArgumentException when the value is not positive
     */
    public static BigDecimal ln(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("logarithm requires a positive value");
        }
        BigDecimal reduced = value;
        int power = 0;
        while (reduced.compareTo(TWO) >= 0) {
            reduced = reduced.divide(TWO, CONTEXT);
            power++;
        }
        while (reduced.compareTo(BigDecimal.ONE) < 0) {
            reduced = reduced.multiply(TWO, CONTEXT);
            power--;
        }
        if (reduced.compareTo(ONE_POINT_FIVE) > 0) {
            reduced = reduced.divide(TWO, CONTEXT);
            power++;
        }
        BigDecimal z = reduced.subtract(BigDecimal.ONE)
                .divide(reduced.add(BigDecimal.ONE, CONTEXT), CONTEXT);
        BigDecimal zSquared = z.multiply(z, CONTEXT);
        BigDecimal term = z;
        BigDecimal sum = z;
        for (int n = 3; n <= 399; n += 2) {
            term = term.multiply(zSquared, CONTEXT);
            BigDecimal addition = term.divide(BigDecimal.valueOf(n), CONTEXT);
            sum = sum.add(addition, CONTEXT);
            if (addition.abs().compareTo(SERIES_TOLERANCE) < 0) {
                break;
            }
        }
        return sum.multiply(TWO, CONTEXT)
                .add(LN_TWO.multiply(BigDecimal.valueOf(power), CONTEXT), CONTEXT);
    }

    /**
     * Square root of a non-negative value.
     */
    public static BigDecimal sqrt(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException("square root requires a non-negative value");
        }
        return value.sqrt(CONTEXT);
    }

    /**
     * Standard normal probability density at the given value.
     */
    public static BigDecimal normalPdf(BigDecimal value) {
        BigDecimal squared = value.multiply(value, CONTEXT);
        BigDecimal exponent = squared.divide(TWO, CONTEXT).negate();
        if (exponent.compareTo(BigDecimal.valueOf(-700)) < 0) {
            return BigDecimal.ZERO;
        }
        return exp(exponent).divide(SQRT_TWO_PI, CONTEXT);
    }

    /**
     * Standard normal cumulative distribution at the given value.
     */
    public static BigDecimal normalCdf(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        if (value.signum() == 0) {
            return HALF;
        }
        boolean negative = value.signum() < 0;
        BigDecimal magnitude = value.abs();
        BigDecimal t = BigDecimal.ONE.divide(
                BigDecimal.ONE.add(AS_P.multiply(magnitude, CONTEXT), CONTEXT), CONTEXT);
        BigDecimal polynomial = AS_B5;
        polynomial = polynomial.multiply(t, CONTEXT).add(AS_B4, CONTEXT);
        polynomial = polynomial.multiply(t, CONTEXT).add(AS_B3, CONTEXT);
        polynomial = polynomial.multiply(t, CONTEXT).add(AS_B2, CONTEXT);
        polynomial = polynomial.multiply(t, CONTEXT).add(AS_B1, CONTEXT);
        polynomial = polynomial.multiply(t, CONTEXT);
        BigDecimal tail = normalPdf(magnitude).multiply(polynomial, CONTEXT);
        if (negative) {
            return clampProbability(tail);
        }
        return clampProbability(BigDecimal.ONE.subtract(tail, CONTEXT));
    }

    /**
     * Compact plain representation used in deterministic summaries.
     */
    public static String format(BigDecimal value) {
        if (value == null) {
            return "unavailable";
        }
        BigDecimal scaled = value.setScale(6, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return scaled.scale() < 0 ? scaled.setScale(0).toPlainString() : scaled.toPlainString();
    }

    private static BigDecimal clampProbability(BigDecimal value) {
        if (value.signum() < 0) {
            return BigDecimal.ZERO;
        }
        if (value.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }
        return value;
    }
}
