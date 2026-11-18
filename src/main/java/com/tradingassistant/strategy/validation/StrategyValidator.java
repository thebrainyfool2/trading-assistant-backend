package com.tradingassistant.strategy.validation;

import com.tradingassistant.strategy.config.StrategyEngineConfig;
import com.tradingassistant.strategy.domain.StrategyDefinition;
import com.tradingassistant.strategy.domain.StrategyEvaluationContext;
import com.tradingassistant.strategy.exception.StrategyValidationException;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic validation of definitions, contexts and evaluation batches.
 *
 * <p>Model constructors reject structurally invalid values; this class adds the
 * limits that depend on configuration and the cross-definition checks that a
 * single definition cannot perform. Every message names the offending field or
 * identifier.</p>
 */
public final class StrategyValidator {

    private StrategyValidator() {
    }

    public static void validateDefinition(StrategyDefinition definition,
                                          StrategyEngineConfig config) {
        Objects.requireNonNull(definition, "definition must not be null");
        Objects.requireNonNull(config, "config must not be null");
        if (definition.rules().size() > config.maxRules()) {
            throw new StrategyValidationException(
                    "strategy " + definition.id() + " exceeds maxRules "
                            + config.maxRules());
        }
    }

    public static void validateContext(StrategyEvaluationContext context) {
        Objects.requireNonNull(context, "context must not be null");
    }

    /**
     * Validates a batch before evaluation: size, null entries and duplicate ids.
     */
    public static void validateBatch(List<StrategyDefinition> strategies,
                                     StrategyEngineConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        if (strategies == null) {
            throw new StrategyValidationException("strategies must not be null");
        }
        if (strategies.size() > config.maxStrategies()) {
            throw new StrategyValidationException(
                    "strategy batch exceeds maxStrategies " + config.maxStrategies());
        }
        Set<String> ids = new HashSet<>();
        for (StrategyDefinition definition : strategies) {
            if (definition == null) {
                throw new StrategyValidationException(
                        "strategies must not contain null entries");
            }
            validateDefinition(definition, config);
            if (!ids.add(definition.id())) {
                throw new StrategyValidationException(
                        "duplicate strategy id " + definition.id());
            }
        }
    }
}
