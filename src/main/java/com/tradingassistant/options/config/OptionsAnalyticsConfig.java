package com.tradingassistant.options.config;

import com.tradingassistant.options.domain.ExpectedMoveMethod;
import com.tradingassistant.options.domain.PcrAggregation;
import com.tradingassistant.options.exception.OptionsValidationException;
import com.tradingassistant.options.math.DecimalMath;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Immutable configuration of the option analytics engine.
 *
 * <p>Every threshold is explicit and validated at construction; there is no
 * runtime mutation, so an assessment is reproducible for a given configuration.
 * Changing behaviour means constructing a new configuration.</p>
 *
 * <p>Volatilities are expressed as decimal fractions: {@code 0.25} is 25%.</p>
 */
public record OptionsAnalyticsConfig(
        BigDecimal atmDistancePercent,
        BigDecimal deepItmDistancePercent,
        BigDecimal deepOtmDistancePercent,
        long minimumOpenInterest,
        long minimumVolume,
        BigDecimal maximumSpreadPercent,
        BigDecimal riskFreeRate,
        BigDecimal dividendYield,
        int dayCount,
        BigDecimal minimumVolatility,
        BigDecimal maximumVolatility,
        int volatilityIterations,
        BigDecimal volatilityTolerance,
        BigDecimal ivTrendThreshold,
        int ivHistoryWindow,
        PcrAggregation pcrAggregation,
        ExpectedMoveMethod expectedMoveMethod,
        int expectedMoveDays,
        int smileStrikeRange,
        int supportResistanceStrikeCount,
        int maxPainNearestStrikeCount,
        ZoneId exchangeZone) {

    public static final OptionsAnalyticsConfig DEFAULT = builder().build();

    public OptionsAnalyticsConfig {
        atmDistancePercent = percent(atmDistancePercent, "atmDistancePercent");
        deepItmDistancePercent = percent(deepItmDistancePercent, "deepItmDistancePercent");
        deepOtmDistancePercent = percent(deepOtmDistancePercent, "deepOtmDistancePercent");
        if (minimumOpenInterest < 0) {
            throw new OptionsValidationException("minimumOpenInterest must not be negative");
        }
        if (minimumVolume < 0) {
            throw new OptionsValidationException("minimumVolume must not be negative");
        }
        maximumSpreadPercent = percent(maximumSpreadPercent, "maximumSpreadPercent");
        if (maximumSpreadPercent.signum() <= 0) {
            throw new OptionsValidationException("maximumSpreadPercent must be positive");
        }
        riskFreeRate = fraction(riskFreeRate, "riskFreeRate");
        dividendYield = fraction(dividendYield, "dividendYield");
        if (dayCount < 1 || dayCount > 366) {
            throw new OptionsValidationException("dayCount must be between 1 and 366");
        }
        minimumVolatility = fraction(minimumVolatility, "minimumVolatility");
        if (minimumVolatility.signum() <= 0) {
            throw new OptionsValidationException("minimumVolatility must be positive");
        }
        maximumVolatility = fraction(maximumVolatility, "maximumVolatility");
        if (maximumVolatility.compareTo(minimumVolatility) <= 0) {
            throw new OptionsValidationException(
                    "maximumVolatility must be greater than minimumVolatility");
        }
        if (volatilityIterations < 1 || volatilityIterations > 1_000) {
            throw new OptionsValidationException(
                    "volatilityIterations must be between 1 and 1000");
        }
        volatilityTolerance = Objects.requireNonNull(volatilityTolerance,
                "volatilityTolerance must not be null");
        if (volatilityTolerance.signum() <= 0) {
            throw new OptionsValidationException("volatilityTolerance must be positive");
        }
        ivTrendThreshold = fraction(ivTrendThreshold, "ivTrendThreshold");
        if (ivHistoryWindow < 2) {
            throw new OptionsValidationException("ivHistoryWindow must be at least 2");
        }
        pcrAggregation = Objects.requireNonNull(pcrAggregation,
                "pcrAggregation must not be null");
        expectedMoveMethod = Objects.requireNonNull(expectedMoveMethod,
                "expectedMoveMethod must not be null");
        if (expectedMoveDays < 1) {
            throw new OptionsValidationException("expectedMoveDays must be positive");
        }
        if (smileStrikeRange < 1) {
            throw new OptionsValidationException("smileStrikeRange must be positive");
        }
        if (supportResistanceStrikeCount < 1) {
            throw new OptionsValidationException(
                    "supportResistanceStrikeCount must be positive");
        }
        if (maxPainNearestStrikeCount < 1) {
            throw new OptionsValidationException("maxPainNearestStrikeCount must be positive");
        }
        exchangeZone = Objects.requireNonNull(exchangeZone, "exchangeZone must not be null");
    }

    /**
     * Fraction of the calendar year used when converting days to expiry.
     */
    public BigDecimal yearFraction(long days) {
        return BigDecimal.valueOf(days)
                .divide(BigDecimal.valueOf(dayCount), DecimalMath.CONTEXT);
    }

    /** Fraction of a year represented by one calendar day. */
    public BigDecimal dailyYearFraction() {
        return BigDecimal.ONE.divide(BigDecimal.valueOf(dayCount), DecimalMath.CONTEXT);
    }

    /** Fraction of a year represented by the configured expected-move horizon. */
    public BigDecimal expectedMoveYearFraction() {
        return yearFraction(expectedMoveDays);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .atmDistancePercent(atmDistancePercent)
                .deepItmDistancePercent(deepItmDistancePercent)
                .deepOtmDistancePercent(deepOtmDistancePercent)
                .minimumOpenInterest(minimumOpenInterest)
                .minimumVolume(minimumVolume)
                .maximumSpreadPercent(maximumSpreadPercent)
                .riskFreeRate(riskFreeRate)
                .dividendYield(dividendYield)
                .dayCount(dayCount)
                .minimumVolatility(minimumVolatility)
                .maximumVolatility(maximumVolatility)
                .volatilityIterations(volatilityIterations)
                .volatilityTolerance(volatilityTolerance)
                .ivTrendThreshold(ivTrendThreshold)
                .ivHistoryWindow(ivHistoryWindow)
                .pcrAggregation(pcrAggregation)
                .expectedMoveMethod(expectedMoveMethod)
                .expectedMoveDays(expectedMoveDays)
                .smileStrikeRange(smileStrikeRange)
                .supportResistanceStrikeCount(supportResistanceStrikeCount)
                .maxPainNearestStrikeCount(maxPainNearestStrikeCount)
                .exchangeZone(exchangeZone);
    }

    private static BigDecimal percent(BigDecimal value, String field) {
        return fraction(value, field);
    }

    private static BigDecimal fraction(BigDecimal value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.signum() < 0) {
            throw new OptionsValidationException(field + " must not be negative");
        }
        return value;
    }

    /**
     * Mutable builder used only while constructing an immutable configuration.
     */
    public static final class Builder {

        private BigDecimal atmDistancePercent = new BigDecimal("0.5");
        private BigDecimal deepItmDistancePercent = BigDecimal.TEN;
        private BigDecimal deepOtmDistancePercent = BigDecimal.TEN;
        private long minimumOpenInterest = 100L;
        private long minimumVolume = 100L;
        private BigDecimal maximumSpreadPercent = new BigDecimal("2.0");
        private BigDecimal riskFreeRate = new BigDecimal("0.065");
        private BigDecimal dividendYield = BigDecimal.ZERO;
        private int dayCount = 365;
        private BigDecimal minimumVolatility = new BigDecimal("0.01");
        private BigDecimal maximumVolatility = new BigDecimal("5.0");
        private int volatilityIterations = 60;
        private BigDecimal volatilityTolerance = new BigDecimal("0.0001");
        private BigDecimal ivTrendThreshold = new BigDecimal("0.005");
        private int ivHistoryWindow = 60;
        private PcrAggregation pcrAggregation = PcrAggregation.BOTH;
        private ExpectedMoveMethod expectedMoveMethod = ExpectedMoveMethod.VOLATILITY;
        private int expectedMoveDays = 7;
        private int smileStrikeRange = 3;
        private int supportResistanceStrikeCount = 3;
        private int maxPainNearestStrikeCount = 3;
        private ZoneId exchangeZone = ZoneId.of("Asia/Kolkata");

        private Builder() {
        }

        public Builder atmDistancePercent(BigDecimal value) {
            this.atmDistancePercent = value;
            return this;
        }

        public Builder deepItmDistancePercent(BigDecimal value) {
            this.deepItmDistancePercent = value;
            return this;
        }

        public Builder deepOtmDistancePercent(BigDecimal value) {
            this.deepOtmDistancePercent = value;
            return this;
        }

        public Builder minimumOpenInterest(long value) {
            this.minimumOpenInterest = value;
            return this;
        }

        public Builder minimumVolume(long value) {
            this.minimumVolume = value;
            return this;
        }

        public Builder maximumSpreadPercent(BigDecimal value) {
            this.maximumSpreadPercent = value;
            return this;
        }

        public Builder riskFreeRate(BigDecimal value) {
            this.riskFreeRate = value;
            return this;
        }

        public Builder dividendYield(BigDecimal value) {
            this.dividendYield = value;
            return this;
        }

        public Builder dayCount(int value) {
            this.dayCount = value;
            return this;
        }

        public Builder minimumVolatility(BigDecimal value) {
            this.minimumVolatility = value;
            return this;
        }

        public Builder maximumVolatility(BigDecimal value) {
            this.maximumVolatility = value;
            return this;
        }

        public Builder volatilityIterations(int value) {
            this.volatilityIterations = value;
            return this;
        }

        public Builder volatilityTolerance(BigDecimal value) {
            this.volatilityTolerance = value;
            return this;
        }

        public Builder ivTrendThreshold(BigDecimal value) {
            this.ivTrendThreshold = value;
            return this;
        }

        public Builder ivHistoryWindow(int value) {
            this.ivHistoryWindow = value;
            return this;
        }

        public Builder pcrAggregation(PcrAggregation value) {
            this.pcrAggregation = value;
            return this;
        }

        public Builder expectedMoveMethod(ExpectedMoveMethod value) {
            this.expectedMoveMethod = value;
            return this;
        }

        public Builder expectedMoveDays(int value) {
            this.expectedMoveDays = value;
            return this;
        }

        public Builder smileStrikeRange(int value) {
            this.smileStrikeRange = value;
            return this;
        }

        public Builder supportResistanceStrikeCount(int value) {
            this.supportResistanceStrikeCount = value;
            return this;
        }

        public Builder maxPainNearestStrikeCount(int value) {
            this.maxPainNearestStrikeCount = value;
            return this;
        }

        public Builder exchangeZone(ZoneId value) {
            this.exchangeZone = value;
            return this;
        }

        public OptionsAnalyticsConfig build() {
            return new OptionsAnalyticsConfig(atmDistancePercent, deepItmDistancePercent,
                    deepOtmDistancePercent, minimumOpenInterest, minimumVolume,
                    maximumSpreadPercent, riskFreeRate, dividendYield, dayCount,
                    minimumVolatility, maximumVolatility, volatilityIterations,
                    volatilityTolerance, ivTrendThreshold, ivHistoryWindow, pcrAggregation,
                    expectedMoveMethod, expectedMoveDays, smileStrikeRange,
                    supportResistanceStrikeCount, maxPainNearestStrikeCount, exchangeZone);
        }
    }
}
