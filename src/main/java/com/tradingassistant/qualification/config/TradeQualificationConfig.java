package com.tradingassistant.qualification.config;

import com.tradingassistant.intelligence.domain.ConfidenceLevel;
import com.tradingassistant.qualification.exception.QualificationValidationException;

import java.util.Objects;

/**
 * Immutable configuration of the trade qualification engine.
 *
 * <p>There is no runtime mutation: changing behaviour means constructing a new
 * configuration. Every limit and every policy is validated at construction, so an
 * engine or registry can never run with an impossible setting.</p>
 *
 * @param enabled                    whether qualification runs at all
 * @param strictValidation           report a missing required input once, per rule,
 *                                   without evaluating rules
 * @param failFast                   propagate an unexpected rule failure instead of
 *                                   containing it as {@code UNKNOWN}
 * @param minimumConfidence          lowest acceptable market confidence level
 * @param requireOptions             whether an option assessment is required
 * @param allowPartialQualification  whether a result whose only undecided rules are
 *                                   data-dependent may be reported as qualified
 * @param maxRules                   maximum number of rules in one qualification
 * @param parallelEvaluation         whether rules may be evaluated concurrently
 */
public record TradeQualificationConfig(
        boolean enabled,
        boolean strictValidation,
        boolean failFast,
        ConfidenceLevel minimumConfidence,
        boolean requireOptions,
        boolean allowPartialQualification,
        int maxRules,
        boolean parallelEvaluation) {

    /** Upper bound for {@code maxRules}; rules are few and deterministic. */
    public static final int MAX_RULES_LIMIT = 256;

    public static final TradeQualificationConfig DEFAULT = builder().build();

    public TradeQualificationConfig {
        minimumConfidence = Objects.requireNonNull(minimumConfidence,
                "minimumConfidence must not be null");
        if (minimumConfidence == ConfidenceLevel.UNKNOWN) {
            throw new QualificationValidationException(
                    "minimumConfidence must be a concrete level");
        }
        if (maxRules < 1 || maxRules > MAX_RULES_LIMIT) {
            throw new QualificationValidationException(
                    "maxRules must be between 1 and " + MAX_RULES_LIMIT);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .enabled(enabled)
                .strictValidation(strictValidation)
                .failFast(failFast)
                .minimumConfidence(minimumConfidence)
                .requireOptions(requireOptions)
                .allowPartialQualification(allowPartialQualification)
                .maxRules(maxRules)
                .parallelEvaluation(parallelEvaluation);
    }

    /**
     * Mutable builder used only while constructing an immutable configuration.
     */
    public static final class Builder {

        private boolean enabled = true;
        private boolean strictValidation = true;
        private boolean failFast = false;
        private ConfidenceLevel minimumConfidence = ConfidenceLevel.HIGH;
        private boolean requireOptions = false;
        private boolean allowPartialQualification = false;
        private int maxRules = 16;
        private boolean parallelEvaluation = false;

        private Builder() {
        }

        public Builder enabled(boolean value) {
            this.enabled = value;
            return this;
        }

        public Builder strictValidation(boolean value) {
            this.strictValidation = value;
            return this;
        }

        public Builder failFast(boolean value) {
            this.failFast = value;
            return this;
        }

        public Builder minimumConfidence(ConfidenceLevel value) {
            this.minimumConfidence = value;
            return this;
        }

        public Builder requireOptions(boolean value) {
            this.requireOptions = value;
            return this;
        }

        public Builder allowPartialQualification(boolean value) {
            this.allowPartialQualification = value;
            return this;
        }

        public Builder maxRules(int value) {
            this.maxRules = value;
            return this;
        }

        public Builder parallelEvaluation(boolean value) {
            this.parallelEvaluation = value;
            return this;
        }

        public TradeQualificationConfig build() {
            return new TradeQualificationConfig(enabled, strictValidation, failFast,
                    minimumConfidence, requireOptions, allowPartialQualification, maxRules,
                    parallelEvaluation);
        }
    }

    /**
     * Ensures the instance is non-null for callers that accept one.
     */
    public static TradeQualificationConfig require(TradeQualificationConfig config) {
        return Objects.requireNonNull(config, "config must not be null");
    }
}
