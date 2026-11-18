package com.tradingassistant.strategy.domain;

import com.tradingassistant.strategy.api.StrategyRule;
import com.tradingassistant.strategy.exception.StrategyValidationException;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable definition of one strategy: identity, descriptive metadata, the
 * inputs it declares, and its ordered rule list.
 *
 * <p>Definitions are validated on construction, so the registry and the engine
 * never have to defend against a blank identifier, an empty rule list or a
 * duplicate rule identifier. A definition carries no mutable state, no score,
 * no ranking and no recommendation.</p>
 *
 * @param id             stable identity, unique in a registry
 * @param name           human-readable name
 * @param description    optional description, empty when none was supplied
 * @param enabled        whether the engine evaluates this strategy
 * @param category       descriptive category
 * @param requiredInputs inputs the strategy declares it needs
 * @param rules          ordered rules, all evaluated independently
 */
public record StrategyDefinition(
        String id,
        String name,
        String description,
        boolean enabled,
        StrategyCategory category,
        Set<StrategyInput> requiredInputs,
        List<StrategyRule> rules) {

    public StrategyDefinition {
        if (id == null || id.isBlank()) {
            throw new StrategyValidationException("id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new StrategyValidationException("name must not be blank");
        }
        category = Objects.requireNonNull(category, "category must not be null");
        if (requiredInputs == null) {
            throw new StrategyValidationException("requiredInputs must not be null");
        }
        if (rules == null || rules.isEmpty()) {
            throw new StrategyValidationException("rules must not be empty");
        }
        if (rules.stream().anyMatch(Objects::isNull)) {
            throw new StrategyValidationException("rules must not contain null entries");
        }
        Set<String> ruleIds = new HashSet<>();
        for (StrategyRule rule : rules) {
            String ruleId = rule.id();
            if (ruleId == null || ruleId.isBlank()) {
                throw new StrategyValidationException("rule id must not be blank");
            }
            if (!ruleIds.add(ruleId.trim())) {
                throw new StrategyValidationException("duplicate rule id " + ruleId.trim());
            }
        }
        id = id.trim();
        name = name.trim();
        description = description == null ? "" : description.trim();
        rules = List.copyOf(rules);
        requiredInputs = Set.copyOf(requiredInputs);
    }

    /**
     * Same definition with a different enabled flag.
     */
    public StrategyDefinition withEnabled(boolean value) {
        return new StrategyDefinition(id, name, description, value, category, requiredInputs,
                rules);
    }

    public int ruleCount() {
        return rules.size();
    }
}
