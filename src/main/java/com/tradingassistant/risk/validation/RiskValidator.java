package com.tradingassistant.risk.validation;

import com.tradingassistant.risk.api.RiskRule;
import com.tradingassistant.risk.config.RiskEngineConfig;
import com.tradingassistant.risk.domain.RiskContext;
import com.tradingassistant.risk.exception.RiskValidationException;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic validation of risk inputs and rule batches.
 *
 * <p>Model constructors reject structurally invalid values, {@link RiskContext}
 * rejects misaligned parts and {@link RiskEngineConfig} rejects an unusable policy.
 * This class adds the checks a service performs at the event boundary and the
 * cross-rule checks a single rule cannot perform. Every message names the offending
 * field or identifier.</p>
 */
public final class RiskValidator {

    private RiskValidator() {
    }

    /**
     * Rejects a null context. Deeper checks live in the context constructor so a
     * context can never hold misaligned data.
     */
    public static void validateContext(RiskContext context) {
        if (context == null) {
            throw new RiskValidationException("context must not be null");
        }
    }

    /**
     * Validates a rule batch: non-null, non-empty, no null entries and no duplicate
     * identifiers.
     */
    public static void validateRules(List<RiskRule> rules, RiskEngineConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        if (rules == null) {
            throw new RiskValidationException("rules must not be null");
        }
        if (rules.isEmpty()) {
            throw new RiskValidationException("rules must not be empty");
        }
        Set<String> ids = new HashSet<>();
        for (RiskRule rule : rules) {
            if (rule == null) {
                throw new RiskValidationException("rules must not contain null entries");
            }
            String id = rule.id();
            if (id == null || id.isBlank()) {
                throw new RiskValidationException("rule id must not be blank");
            }
            if (!ids.add(id)) {
                throw new RiskValidationException("duplicate rule id " + id);
            }
        }
    }

    /**
     * Rejects a configuration that would make sizing meaningless.
     */
    public static void validateConfig(RiskEngineConfig config) {
        if (config == null) {
            throw new RiskValidationException("config must not be null");
        }
    }
}
