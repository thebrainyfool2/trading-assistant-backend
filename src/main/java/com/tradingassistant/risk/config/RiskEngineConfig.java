package com.tradingassistant.risk.config;

import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.risk.exception.RiskValidationException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable risk policy.
 *
 * <p>{@code referenceCapital} is a sizing reference, not account state: nothing in
 * this layer reads, synchronizes or tracks a real account. Changing a policy means
 * constructing a new configuration; there is no runtime mutation.</p>
 *
 * <p>Every value is validated at construction so an assessment can never run with
 * a zero budget, an inverted stop range or an unusable leverage ceiling.</p>
 *
 * @param enabled                        whether risk assessment runs at all
 * @param referenceCapital               capital used for every sizing calculation
 * @param maximumRiskPercent             risk budget per assessment, as a percentage
 * @param maximumDailyLossPercent        daily loss ceiling, as a percentage of capital
 * @param maximumSessionLossPercent      session loss ceiling, as a percentage of capital
 * @param maximumOpenExposurePercent     exposure ceiling, as a percentage of capital
 * @param defaultATRMultiplier           ATR multiple before the volatility adjustment
 * @param minimumRewardRiskRatio         reward-to-risk floor
 * @param minimumStopDistance            smallest permitted stop distance
 * @param maximumStopDistance            largest permitted stop distance
 * @param maximumLeverage                leverage ceiling
 * @param maximumQuantity                largest permitted quantity
 * @param allowFractionalQuantity        whether fractional units are permitted
 * @param instrumentRiskPercentOverrides per-instrument risk percentages
 * @param parallelEvaluation             whether rules may be evaluated concurrently
 * @param failFast                       propagate an unexpected rule failure
 * @param strictValidation               report a missing plan once, without evaluating rules
 */
public record RiskEngineConfig(
        boolean enabled,
        BigDecimal referenceCapital,
        BigDecimal maximumRiskPercent,
        BigDecimal maximumDailyLossPercent,
        BigDecimal maximumSessionLossPercent,
        BigDecimal maximumOpenExposurePercent,
        BigDecimal defaultATRMultiplier,
        BigDecimal minimumRewardRiskRatio,
        BigDecimal minimumStopDistance,
        BigDecimal maximumStopDistance,
        BigDecimal maximumLeverage,
        BigDecimal maximumQuantity,
        boolean allowFractionalQuantity,
        Map<InstrumentKey, BigDecimal> instrumentRiskPercentOverrides,
        boolean parallelEvaluation,
        boolean failFast,
        boolean strictValidation) {

    public static final RiskEngineConfig DEFAULT = builder().build();

    public RiskEngineConfig {
        referenceCapital = requirePositive(referenceCapital, "referenceCapital");
        maximumRiskPercent = requirePositive(maximumRiskPercent, "maximumRiskPercent");
        requireAtMost(maximumRiskPercent, BigDecimal.valueOf(100), "maximumRiskPercent");
        maximumDailyLossPercent = requirePositive(maximumDailyLossPercent,
                "maximumDailyLossPercent");
        requireAtMost(maximumDailyLossPercent, BigDecimal.valueOf(100),
                "maximumDailyLossPercent");
        maximumSessionLossPercent = requirePositive(maximumSessionLossPercent,
                "maximumSessionLossPercent");
        if (maximumSessionLossPercent.compareTo(maximumDailyLossPercent) > 0) {
            throw new RiskValidationException(
                    "maximumSessionLossPercent must not exceed maximumDailyLossPercent");
        }
        maximumOpenExposurePercent = requirePositive(maximumOpenExposurePercent,
                "maximumOpenExposurePercent");
        requireAtMost(maximumOpenExposurePercent, BigDecimal.valueOf(1_000),
                "maximumOpenExposurePercent");
        defaultATRMultiplier = requirePositive(defaultATRMultiplier, "defaultATRMultiplier");
        minimumRewardRiskRatio = requirePositive(minimumRewardRiskRatio,
                "minimumRewardRiskRatio");
        minimumStopDistance = requirePositive(minimumStopDistance, "minimumStopDistance");
        maximumStopDistance = requirePositive(maximumStopDistance, "maximumStopDistance");
        if (maximumStopDistance.compareTo(minimumStopDistance) <= 0) {
            throw new RiskValidationException(
                    "maximumStopDistance must be greater than minimumStopDistance");
        }
        maximumLeverage = requirePositive(maximumLeverage, "maximumLeverage");
        requireAtMost(maximumLeverage, BigDecimal.valueOf(100), "maximumLeverage");
        maximumQuantity = requirePositive(maximumQuantity, "maximumQuantity");
        instrumentRiskPercentOverrides = validateOverrides(instrumentRiskPercentOverrides,
                maximumRiskPercent);
    }

    /**
     * Builds a configuration with the documented defaults.
     */
    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .enabled(enabled)
                .referenceCapital(referenceCapital)
                .maximumRiskPercent(maximumRiskPercent)
                .maximumDailyLossPercent(maximumDailyLossPercent)
                .maximumSessionLossPercent(maximumSessionLossPercent)
                .maximumOpenExposurePercent(maximumOpenExposurePercent)
                .defaultATRMultiplier(defaultATRMultiplier)
                .minimumRewardRiskRatio(minimumRewardRiskRatio)
                .minimumStopDistance(minimumStopDistance)
                .maximumStopDistance(maximumStopDistance)
                .maximumLeverage(maximumLeverage)
                .maximumQuantity(maximumQuantity)
                .allowFractionalQuantity(allowFractionalQuantity)
                .instrumentRiskPercentOverrides(instrumentRiskPercentOverrides)
                .parallelEvaluation(parallelEvaluation)
                .failFast(failFast)
                .strictValidation(strictValidation);
    }

    /**
     * Risk percentage for an instrument: its override when configured, otherwise
     * the global maximum.
     */
    public BigDecimal riskPercentFor(InstrumentKey instrument) {
        if (instrument == null) {
            return maximumRiskPercent;
        }
        return instrumentRiskPercentOverrides.getOrDefault(instrument, maximumRiskPercent);
    }

    public boolean hasOverride(InstrumentKey instrument) {
        return instrument != null && instrumentRiskPercentOverrides.containsKey(instrument);
    }

    /**
     * Maximum loss the daily policy allows.
     */
    public BigDecimal dailyLossLimit() {
        return percentOf(referenceCapital, maximumDailyLossPercent);
    }

    /**
     * Maximum loss the session policy allows.
     */
    public BigDecimal sessionLossLimit() {
        return percentOf(referenceCapital, maximumSessionLossPercent);
    }

    /**
     * Maximum open exposure the policy allows.
     */
    public BigDecimal maximumExposure() {
        return percentOf(referenceCapital, maximumOpenExposurePercent);
    }

    /**
     * Percentage of a value, rounded to eight decimal places only where division is
     * unavoidable.
     */
    public static BigDecimal percentOf(BigDecimal value, BigDecimal percent) {
        return value.multiply(percent).divide(BigDecimal.valueOf(100),
                java.math.MathContext.DECIMAL128);
    }

    public static RiskEngineConfig require(RiskEngineConfig config) {
        return Objects.requireNonNull(config, "config must not be null");
    }

    private static BigDecimal requirePositive(BigDecimal value, String field) {
        if (value == null) {
            throw new RiskValidationException(field + " must not be null");
        }
        if (value.signum() <= 0) {
            throw new RiskValidationException(field + " must be greater than zero");
        }
        return value;
    }

    private static void requireAtMost(BigDecimal value, BigDecimal maximum, String field) {
        if (value.compareTo(maximum) > 0) {
            throw new RiskValidationException(
                    field + " must not exceed " + maximum.toPlainString());
        }
    }

    private static Map<InstrumentKey, BigDecimal> validateOverrides(
            Map<InstrumentKey, BigDecimal> overrides, BigDecimal maximumRiskPercent) {
        if (overrides == null || overrides.isEmpty()) {
            return Map.of();
        }
        Map<InstrumentKey, BigDecimal> copy = new LinkedHashMap<>();
        overrides.forEach((instrument, percent) -> {
            if (instrument == null) {
                throw new RiskValidationException(
                        "instrumentRiskPercentOverrides must not contain a null instrument");
            }
            if (percent == null || percent.signum() <= 0) {
                throw new RiskValidationException(
                        "instrument risk percentage must be greater than zero");
            }
            if (percent.compareTo(maximumRiskPercent) > 0) {
                throw new RiskValidationException(
                        "instrument risk percentage must not exceed maximumRiskPercent");
            }
            copy.put(instrument, percent);
        });
        return Map.copyOf(copy);
    }

    /**
     * Mutable builder used only while constructing an immutable configuration.
     */
    public static final class Builder {

        private boolean enabled = true;
        private BigDecimal referenceCapital = new BigDecimal("100000");
        private BigDecimal maximumRiskPercent = new BigDecimal("1");
        private BigDecimal maximumDailyLossPercent = new BigDecimal("3");
        private BigDecimal maximumSessionLossPercent = new BigDecimal("2");
        private BigDecimal maximumOpenExposurePercent = new BigDecimal("100");
        private BigDecimal defaultATRMultiplier = new BigDecimal("2");
        private BigDecimal minimumRewardRiskRatio = new BigDecimal("2");
        private BigDecimal minimumStopDistance = new BigDecimal("0.05");
        private BigDecimal maximumStopDistance = new BigDecimal("100");
        private BigDecimal maximumLeverage = new BigDecimal("5");
        private BigDecimal maximumQuantity = new BigDecimal("10000");
        private boolean allowFractionalQuantity = false;
        private Map<InstrumentKey, BigDecimal> instrumentRiskPercentOverrides = Map.of();
        private boolean parallelEvaluation = false;
        private boolean failFast = false;
        private boolean strictValidation = true;

        private Builder() {
        }

        public Builder enabled(boolean value) {
            this.enabled = value;
            return this;
        }

        public Builder referenceCapital(BigDecimal value) {
            this.referenceCapital = value;
            return this;
        }

        public Builder maximumRiskPercent(BigDecimal value) {
            this.maximumRiskPercent = value;
            return this;
        }

        public Builder maximumDailyLossPercent(BigDecimal value) {
            this.maximumDailyLossPercent = value;
            return this;
        }

        public Builder maximumSessionLossPercent(BigDecimal value) {
            this.maximumSessionLossPercent = value;
            return this;
        }

        public Builder maximumOpenExposurePercent(BigDecimal value) {
            this.maximumOpenExposurePercent = value;
            return this;
        }

        public Builder defaultATRMultiplier(BigDecimal value) {
            this.defaultATRMultiplier = value;
            return this;
        }

        public Builder minimumRewardRiskRatio(BigDecimal value) {
            this.minimumRewardRiskRatio = value;
            return this;
        }

        public Builder minimumStopDistance(BigDecimal value) {
            this.minimumStopDistance = value;
            return this;
        }

        public Builder maximumStopDistance(BigDecimal value) {
            this.maximumStopDistance = value;
            return this;
        }

        public Builder maximumLeverage(BigDecimal value) {
            this.maximumLeverage = value;
            return this;
        }

        public Builder maximumQuantity(BigDecimal value) {
            this.maximumQuantity = value;
            return this;
        }

        public Builder allowFractionalQuantity(boolean value) {
            this.allowFractionalQuantity = value;
            return this;
        }

        public Builder instrumentRiskPercentOverrides(Map<InstrumentKey, BigDecimal> value) {
            this.instrumentRiskPercentOverrides = value;
            return this;
        }

        public Builder parallelEvaluation(boolean value) {
            this.parallelEvaluation = value;
            return this;
        }

        public Builder failFast(boolean value) {
            this.failFast = value;
            return this;
        }

        public Builder strictValidation(boolean value) {
            this.strictValidation = value;
            return this;
        }

        public RiskEngineConfig build() {
            return new RiskEngineConfig(enabled, referenceCapital, maximumRiskPercent,
                    maximumDailyLossPercent, maximumSessionLossPercent,
                    maximumOpenExposurePercent, defaultATRMultiplier, minimumRewardRiskRatio,
                    minimumStopDistance, maximumStopDistance, maximumLeverage, maximumQuantity,
                    allowFractionalQuantity, instrumentRiskPercentOverrides, parallelEvaluation,
                    failFast, strictValidation);
        }
    }
}
