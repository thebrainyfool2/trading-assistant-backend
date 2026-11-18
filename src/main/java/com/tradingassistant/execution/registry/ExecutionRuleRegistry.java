package com.tradingassistant.execution.registry;

import com.tradingassistant.execution.api.ExecutionRule;
import com.tradingassistant.execution.exception.ExecutionValidationException;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of immutable execution rules.
 */
public final class ExecutionRuleRegistry {

    private static final Comparator<ExecutionRule> BY_ID = Comparator.comparing(ExecutionRule::id);

    private final ConcurrentHashMap<String, ExecutionRule> rules = new ConcurrentHashMap<>();

    public ExecutionRuleRegistry() {
    }

    public ExecutionRuleRegistry(Collection<ExecutionRule> initial) {
        Objects.requireNonNull(initial, "initial must not be null");
        for (ExecutionRule rule : initial) {
            register(rule);
        }
    }

    public void register(ExecutionRule rule) {
        requireRule(rule);
        ExecutionRule existing = rules.putIfAbsent(rule.id().trim(), rule);
        if (existing != null) {
            throw new ExecutionValidationException("duplicate rule id " + rule.id());
        }
    }

    public void replace(ExecutionRule rule) {
        requireRule(rule);
        if (rules.replace(rule.id().trim(), rule) == null) {
            throw new ExecutionValidationException("unknown rule id " + rule.id());
        }
    }

    public boolean unregister(String id) {
        return id != null && rules.remove(id.trim()) != null;
    }

    public Optional<ExecutionRule> lookup(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(rules.get(id.trim()));
    }

    public boolean contains(String id) {
        return id != null && rules.containsKey(id.trim());
    }

    public List<ExecutionRule> snapshot() {
        return rules.values().stream().sorted(BY_ID).toList();
    }

    public int size() {
        return rules.size();
    }

    private void requireRule(ExecutionRule rule) {
        if (rule == null) {
            throw new ExecutionValidationException("rule must not be null");
        }
        if (rule.id() == null || rule.id().isBlank()) {
            throw new ExecutionValidationException("rule id must not be blank");
        }
    }
}
