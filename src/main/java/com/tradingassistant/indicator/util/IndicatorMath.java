package com.tradingassistant.indicator.util;

import com.tradingassistant.indicator.exception.IndicatorCalculationException;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * BigDecimal-only arithmetic shared by indicators. Exact addition,
 * subtraction and multiplication are retained; divisions and square roots use
 * DECIMAL128 to avoid binary floating-point error.
 */
public final class IndicatorMath {

    public static final MathContext DIVISION_CONTEXT = MathContext.DECIMAL128;
    public static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private IndicatorMath() {
    }

    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        if (dividend == null || divisor == null) {
            throw new IndicatorCalculationException("Division operands must not be null.");
        }
        if (divisor.signum() == 0) {
            throw new IndicatorCalculationException("Division by zero is not defined.");
        }
        return dividend.divide(divisor, DIVISION_CONTEXT);
    }

    public static BigDecimal sqrt(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new IndicatorCalculationException("Square root requires a non-negative value.");
        }
        return value.sqrt(DIVISION_CONTEXT);
    }

    public static BigDecimal average(Iterable<BigDecimal> values, int count) {
        if (count <= 0) {
            throw new IndicatorCalculationException("Average count must be positive.");
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            sum = sum.add(value);
        }
        return divide(sum, BigDecimal.valueOf(count));
    }

    public static BigDecimal min(Iterable<BigDecimal> values) {
        BigDecimal min = null;
        for (BigDecimal value : values) {
            if (min == null || value.compareTo(min) < 0) {
                min = value;
            }
        }
        if (min == null) {
            throw new IndicatorCalculationException("Cannot find a minimum of no values.");
        }
        return min;
    }

    public static BigDecimal max(Iterable<BigDecimal> values) {
        BigDecimal max = null;
        for (BigDecimal value : values) {
            if (max == null || value.compareTo(max) > 0) {
                max = value;
            }
        }
        if (max == null) {
            throw new IndicatorCalculationException("Cannot find a maximum of no values.");
        }
        return max;
    }

    public static BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return divide(numerator, denominator).multiply(ONE_HUNDRED);
    }

}
