package com.tradingassistant.intelligence.config;

import com.tradingassistant.intelligence.exception.IntelligenceValidationException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Immutable configuration of the market-intelligence engine.
 *
 * <p>Every knob is validated once, at construction. There is no runtime
 * mutation: changing a threshold means constructing a new configuration, which
 * keeps an assessment reproducible for a given configuration.</p>
 *
 * <p>Thresholds are expressed in the units of the indicator that consumes them:
 * ADX points, oscillator points, a fraction of a previous value, and a
 * percentage of price for the structure range tolerance.</p>
 *
 * @param trendAgreementVotes             how many trend inputs must agree on a direction
 * @param trendAdxThreshold               ADX value at or above which trend strength is confirmed
 * @param requireAdxForDirection          when true, no direction is reported without ADX confirmation
 * @param momentumUpperThreshold          oscillator level at or above which momentum is strengthening
 * @param momentumLowerThreshold          oscillator level at or below which momentum is weakening
 * @param volatilityChangeThreshold       relative change in range width that counts as a move
 * @param participationCmfThreshold       absolute CMF level that counts as strong or weak participation
 * @param structureLookback               candles used by the structure analyzer
 * @param structureRangeTolerancePercent  allowed high/low drift that still counts as a range, in percent
 * @param historyWindow                   candles and indicator results retained per instrument and interval
 * @param openingWindow                   length of the opening phase measured from the session open
 * @param closingWindow                   length of the closing phase measured to the session close
 * @param warmupPolicy                    behaviour for warming-up or absent inputs
 * @param highConfidenceAspectCount       ready aspects needed for high confidence
 * @param mediumConfidenceAspectCount     ready aspects needed for medium confidence
 * @param requiredIndicators              indicator types that must be present in every analysis context
 */
public record MarketIntelligenceConfig(
        int trendAgreementVotes,
        BigDecimal trendAdxThreshold,
        boolean requireAdxForDirection,
        BigDecimal momentumUpperThreshold,
        BigDecimal momentumLowerThreshold,
        BigDecimal volatilityChangeThreshold,
        BigDecimal participationCmfThreshold,
        int structureLookback,
        BigDecimal structureRangeTolerancePercent,
        int historyWindow,
        Duration openingWindow,
        Duration closingWindow,
        WarmupPolicy warmupPolicy,
        int highConfidenceAspectCount,
        int mediumConfidenceAspectCount,
        Set<String> requiredIndicators) {

    /**
     * Number of market aspects merged into one assessment. Session context is
     * reported alongside them but is not one of them: it is always resolvable
     * from the calendar and never says anything about price behaviour.
     */
    public static final int MARKET_ASPECT_COUNT = 5;

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public MarketIntelligenceConfig {
        if (trendAgreementVotes < 1 || trendAgreementVotes > 3) {
            throw new IntelligenceValidationException("trendAgreementVotes must be between 1 and 3");
        }
        trendAdxThreshold = nonNegative(trendAdxThreshold, "trendAdxThreshold");
        momentumUpperThreshold = nonNegative(momentumUpperThreshold, "momentumUpperThreshold");
        momentumLowerThreshold = nonNegative(momentumLowerThreshold, "momentumLowerThreshold");
        if (momentumLowerThreshold.compareTo(momentumUpperThreshold) >= 0) {
            throw new IntelligenceValidationException(
                    "momentumLowerThreshold must be below momentumUpperThreshold");
        }
        if (momentumUpperThreshold.compareTo(ONE_HUNDRED) > 0) {
            throw new IntelligenceValidationException("momentumUpperThreshold must not exceed 100");
        }
        volatilityChangeThreshold = nonNegative(volatilityChangeThreshold, "volatilityChangeThreshold");
        participationCmfThreshold = nonNegative(participationCmfThreshold, "participationCmfThreshold");
        if (structureLookback < 4) {
            throw new IntelligenceValidationException("structureLookback must be at least 4");
        }
        structureRangeTolerancePercent = nonNegative(
                structureRangeTolerancePercent, "structureRangeTolerancePercent");
        if (historyWindow < structureLookback) {
            throw new IntelligenceValidationException(
                    "historyWindow must be at least structureLookback");
        }
        openingWindow = positive(openingWindow, "openingWindow");
        closingWindow = positive(closingWindow, "closingWindow");
        warmupPolicy = Objects.requireNonNull(warmupPolicy, "warmupPolicy must not be null");
        if (mediumConfidenceAspectCount < 1 || highConfidenceAspectCount <= mediumConfidenceAspectCount) {
            throw new IntelligenceValidationException(
                    "confidence thresholds must satisfy 1 <= medium < high");
        }
        if (highConfidenceAspectCount > MARKET_ASPECT_COUNT) {
            throw new IntelligenceValidationException(
                    "highConfidenceAspectCount must not exceed " + MARKET_ASPECT_COUNT);
        }
        requiredIndicators = canonicalTypes(requiredIndicators);
    }

    /**
     * Balanced defaults: two agreeing trend inputs, ADX confirmation, the usual
     * 60/40 momentum bands, a five-percent volatility change, and permissive
     * warm-up handling that still reports the gap.
     */
    public static MarketIntelligenceConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * True when the indicator type is one this configuration requires.
     */
    public boolean requires(String indicatorType) {
        return indicatorType != null
                && requiredIndicators.contains(indicatorType.trim().toUpperCase(Locale.ROOT));
    }

    private static BigDecimal nonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new IntelligenceValidationException(field + " must be zero or positive");
        }
        return value;
    }

    private static Duration positive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IntelligenceValidationException(field + " must be positive");
        }
        return value;
    }

    private static Set<String> canonicalTypes(Set<String> types) {
        if (types == null) {
            return Set.of();
        }
        TreeSet<String> sorted = new TreeSet<>();
        for (String type : types) {
            if (type == null || type.isBlank()) {
                throw new IntelligenceValidationException("required indicator types must not be blank");
            }
            sorted.add(type.trim().toUpperCase(Locale.ROOT));
        }
        return Set.copyOf(new LinkedHashSet<>(sorted));
    }

    /**
     * Construction helper. A builder is only ever used to produce one immutable
     * configuration; it is never shared as runtime state.
     */
    public static final class Builder {

        private int trendAgreementVotes = 2;
        private BigDecimal trendAdxThreshold = BigDecimal.valueOf(25);
        private boolean requireAdxForDirection = true;
        private BigDecimal momentumUpperThreshold = BigDecimal.valueOf(60);
        private BigDecimal momentumLowerThreshold = BigDecimal.valueOf(40);
        private BigDecimal volatilityChangeThreshold = new BigDecimal("0.05");
        private BigDecimal participationCmfThreshold = new BigDecimal("0.05");
        private int structureLookback = 20;
        private BigDecimal structureRangeTolerancePercent = new BigDecimal("0.5");
        private int historyWindow = 120;
        private Duration openingWindow = Duration.ofMinutes(15);
        private Duration closingWindow = Duration.ofMinutes(15);
        private WarmupPolicy warmupPolicy = WarmupPolicy.PERMISSIVE;
        private int highConfidenceAspectCount = 4;
        private int mediumConfidenceAspectCount = 2;
        private Set<String> requiredIndicators = Set.of();

        private Builder() {
        }

        public Builder trendAgreementVotes(int value) {
            this.trendAgreementVotes = value;
            return this;
        }

        public Builder trendAdxThreshold(BigDecimal value) {
            this.trendAdxThreshold = value;
            return this;
        }

        public Builder requireAdxForDirection(boolean value) {
            this.requireAdxForDirection = value;
            return this;
        }

        public Builder momentumThresholds(BigDecimal lower, BigDecimal upper) {
            this.momentumLowerThreshold = lower;
            this.momentumUpperThreshold = upper;
            return this;
        }

        public Builder volatilityChangeThreshold(BigDecimal value) {
            this.volatilityChangeThreshold = value;
            return this;
        }

        public Builder participationCmfThreshold(BigDecimal value) {
            this.participationCmfThreshold = value;
            return this;
        }

        public Builder structureLookback(int value) {
            this.structureLookback = value;
            return this;
        }

        public Builder structureRangeTolerancePercent(BigDecimal value) {
            this.structureRangeTolerancePercent = value;
            return this;
        }

        public Builder historyWindow(int value) {
            this.historyWindow = value;
            return this;
        }

        public Builder sessionWindows(Duration opening, Duration closing) {
            this.openingWindow = opening;
            this.closingWindow = closing;
            return this;
        }

        public Builder warmupPolicy(WarmupPolicy value) {
            this.warmupPolicy = value;
            return this;
        }

        public Builder confidenceThresholds(int medium, int high) {
            this.mediumConfidenceAspectCount = medium;
            this.highConfidenceAspectCount = high;
            return this;
        }

        public Builder requiredIndicators(Set<String> value) {
            this.requiredIndicators = value;
            return this;
        }

        public MarketIntelligenceConfig build() {
            return new MarketIntelligenceConfig(
                    trendAgreementVotes,
                    trendAdxThreshold,
                    requireAdxForDirection,
                    momentumUpperThreshold,
                    momentumLowerThreshold,
                    volatilityChangeThreshold,
                    participationCmfThreshold,
                    structureLookback,
                    structureRangeTolerancePercent,
                    historyWindow,
                    openingWindow,
                    closingWindow,
                    warmupPolicy,
                    highConfidenceAspectCount,
                    mediumConfidenceAspectCount,
                    requiredIndicators);
        }
    }
}
