package com.tradingassistant.risk.registry;

import com.tradingassistant.risk.api.RiskRule;
import com.tradingassistant.risk.exception.RiskValidationException;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of immutable risk rules.
 *
 * <p>Registration, replacement and removal are atomic per rule; snapshots are
 * immutable copies sorted by identifier, so one assessment always sees one
 * consistent, ordered rule set. Rules are never mutated: changing a boundary means
 * registering a new rule instance. Duplicate identifiers are rejected.</p>
 */
public final class RiskRuleRegistry {

    private static final Comparator<RiskRule> BY_ID = Comparator.comparing(RiskRule::id);

    private final ConcurrentHashMap<String, RiskRule> rules = new ConcurrentHashMap<>();

    public RiskRuleRegistry() {
    }

    public RiskRuleRegistry(Collection<RiskRule> initial) {
        Objects.requireNonNull(initial, "initial must not be null");
        for (RiskRule rule : initial) {
            register(rule);
        }
    }

    /**
     * Registers a rule, rejecting a duplicate identifier.
     *
     * @throws RiskValidationException when the identifier already exists
     */
    public void register(RiskRule rule) {
        requireRule(rule);
        RiskRule existing = rules.putIfAbsent(rule.id().trim(), rule);
        if (existing != null) {
            throw new RiskValidationException("duplicate rule id " + rule.id());
        }
    }

    /**
     * Replaces an existing rule with an immutable instance of the same id.
     *
     * @throws RiskValidationException when no rule carries the id
     */
    public void replace(RiskRule rule) {
        requireRule(rule);
        boolean replaced = rules.replace(rule.id().trim(), rule) != null;
        if (!replaced) {
            throw new RiskValidationException("unknown rule id " + rule.id());
        }
    }

    /**
     * Removes a rule by id.
     *
     * @return true when a rule was removed
     */
    public boolean unregister(String id) {
        return id != null && rules.remove(id.trim()) != null;
    }

    public Optional<RiskRule> lookup(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(rules.get(id.trim()));
    }

    public boolean contains(String id) {
        return id != null && rules.containsKey(id.trim());
    }

    /**
     * Immutable snapshot of every registered rule, ordered by identifier.
     */
    public List<RiskRule> snapshot() {
        return rules.values().stream().sorted(BY_ID).toList();
    }

    public int size() {
        return rules.size();
    }

    private void requireRule(RiskRule rule) {
        if (rule == null) {
            throw new RiskValidationException("rule must not be null");
        }
        if (rule.id() == null || rule.id().isBlank()) {
            throw new RiskValidationException("rule id must not be blank");
        }
    }
}
