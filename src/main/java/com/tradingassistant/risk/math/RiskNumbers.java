package com.tradingassistant.risk.math;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deterministic numeric helpers shared by the calculator and the rules.
 */
public final class RiskNumbers {

    /** Precision used for every division so output is reproducible. */
    public static final MathContext PRECISION = MathContext.DECIMAL128;

    private RiskNumbers() {
    }

    /**
     * Formats a value without trailing zeros, for logs, metadata and summaries.
     */
    public static String format(BigDecimal value) {
        if (value == null) {
            return "unavailable";
        }
        BigDecimal scaled = value.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros();
        return scaled.scale() < 0 ? scaled.setScale(0).toPlainString() : scaled.toPlainString();
    }

    /**
     * Divides two values with the shared precision, returning zero when the divisor
     * is zero.
     */
    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        if (dividend == null || divisor == null || divisor.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return dividend.divide(divisor, PRECISION);
    }

    /**
     * A value as a percentage of a base, zero when the base is zero.
     */
    public static BigDecimal percentOf(BigDecimal value, BigDecimal base) {
        return divide(value, base).multiply(BigDecimal.valueOf(100));
    }

    /**
     * Deterministic metadata from key/value pairs.
     */
    public static Map<String, String> metadata(String... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("metadata requires key/value pairs");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < keysAndValues.length; index += 2) {
            values.put(keysAndValues[index], keysAndValues[index + 1]);
        }
        return Map.copyOf(values);
    }
}
