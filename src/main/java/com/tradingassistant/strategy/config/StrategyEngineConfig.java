package com.tradingassistant.strategy.config;

import com.tradingassistant.strategy.exception.StrategyValidationException;

import java.util.Objects;

/**
 * Immutable configuration of the strategy evaluation engine.
 *
 * <p>There is no runtime mutation: changing behaviour means constructing a new
 * configuration. Limits are validated at construction so a registry or engine
 * can never accept a strategy list that exceeds them.</p>
 *
 * @param enabled            whether the service evaluates strategies at all
 * @param parallelEvaluation whether strategies may be evaluated concurrently
 * @param maxStrategies      maximum number of strategies in one evaluation
 * @param maxRules           maximum number of rules in one definition
 * @param failFast           propagate an unexpected rule failure instead of containing it
 * @param strictValidation   treat a missing declared input as insufficient data
 */
public record StrategyEngineConfig(
        boolean enabled,
        boolean parallelEvaluation,
        int maxStrategies,
        int maxRules,
        boolean failFast,
        boolean strictValidation) {

    public static final StrategyEngineConfig DEFAULT = builder().build();

    public StrategyEngineConfig {
        if (maxStrategies < 1 || maxStrategies > 10_000) {
            throw new StrategyValidationException(
                    "maxStrategies must be between 1 and 10000");
        }
        if (maxRules < 1 || maxRules > 1_000) {
            throw new StrategyValidationException("maxRules must be between 1 and 1000");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .enabled(enabled)
                .parallelEvaluation(parallelEvaluation)
                .maxStrategies(maxStrategies)
                .maxRules(maxRules)
                .failFast(failFast)
                .strictValidation(strictValidation);
    }

    /**
     * Mutable builder used only while constructing an immutable configuration.
     */
    public static final class Builder {

        private boolean enabled = true;
        private boolean parallelEvaluation = false;
        private int maxStrategies = 128;
        private int maxRules = 32;
        private boolean failFast = false;
        private boolean strictValidation = true;

        private Builder() {
        }

        public Builder enabled(boolean value) {
            this.enabled = value;
            return this;
        }

        public Builder parallelEvaluation(boolean value) {
            this.parallelEvaluation = value;
            return this;
        }

        public Builder maxStrategies(int value) {
            this.maxStrategies = value;
            return this;
        }

        public Builder maxRules(int value) {
            this.maxRules = value;
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

        public StrategyEngineConfig build() {
            return new StrategyEngineConfig(enabled, parallelEvaluation, maxStrategies, maxRules,
                    failFast, strictValidation);
        }
    }

    /**
     * Ensures the instance is non-null for callers that accept one.
     */
    public static StrategyEngineConfig require(StrategyEngineConfig config) {
        return Objects.requireNonNull(config, "config must not be null");
    }
}
