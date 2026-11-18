package com.tradingassistant.qualification.registry;

import com.tradingassistant.qualification.api.QualificationRule;
import com.tradingassistant.qualification.config.TradeQualificationConfig;
import com.tradingassistant.qualification.exception.QualificationValidationException;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of immutable qualification rules.
 *
 * <p>Registration, replacement and removal are atomic per rule; snapshots are
 * immutable copies sorted by identifier, so one qualification always sees one
 * consistent, ordered rule set. Rules themselves are never mutated: changing a
 * threshold means registering a new rule instance. The registry enforces duplicate
 * detection and the configured rule limit.</p>
 */
public final class QualificationRuleRegistry {

    private static final Comparator<QualificationRule> BY_ID =
            Comparator.comparing(QualificationRule::id);

    private final TradeQualificationConfig config;
    private final ConcurrentHashMap<String, QualificationRule> rules = new ConcurrentHashMap<>();

    public QualificationRuleRegistry(TradeQualificationConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    public QualificationRuleRegistry(TradeQualificationConfig config,
                                     Collection<QualificationRule> initial) {
        this(config);
        Objects.requireNonNull(initial, "initial must not be null");
        for (QualificationRule rule : initial) {
            register(rule);
        }
    }

    /**
     * Registers a rule, rejecting a duplicate identifier.
     *
     * @throws QualificationValidationException when the identifier already exists or
     *                                          the configured limit is exceeded
     */
    public void register(QualificationRule rule) {
        requireRule(rule);
        if (rules.size() >= config.maxRules()) {
            throw new QualificationValidationException(
                    "registry exceeds maxRules " + config.maxRules());
        }
        QualificationRule existing = rules.putIfAbsent(rule.id().trim(), rule);
        if (existing != null) {
            throw new QualificationValidationException("duplicate rule id " + rule.id());
        }
    }

    /**
     * Replaces an existing rule with an immutable instance of the same id.
     *
     * @throws QualificationValidationException when no rule carries the id
     */
    public void replace(QualificationRule rule) {
        requireRule(rule);
        boolean replaced = rules.replace(rule.id().trim(), rule) != null;
        if (!replaced) {
            throw new QualificationValidationException("unknown rule id " + rule.id());
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

    public Optional<QualificationRule> lookup(String id) {
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
    public List<QualificationRule> snapshot() {
        return rules.values().stream().sorted(BY_ID).toList();
    }

    public int size() {
        return rules.size();
    }

    public TradeQualificationConfig config() {
        return config;
    }

    private void requireRule(QualificationRule rule) {
        if (rule == null) {
            throw new QualificationValidationException("rule must not be null");
        }
        if (rule.id() == null || rule.id().isBlank()) {
            throw new QualificationValidationException("rule id must not be blank");
        }
    }
}
