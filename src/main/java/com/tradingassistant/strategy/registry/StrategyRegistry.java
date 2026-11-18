package com.tradingassistant.strategy.registry;

import com.tradingassistant.strategy.config.StrategyEngineConfig;
import com.tradingassistant.strategy.domain.StrategyDefinition;
import com.tradingassistant.strategy.exception.StrategyValidationException;
import com.tradingassistant.strategy.validation.StrategyValidator;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of immutable strategy definitions.
 *
 * <p>Registration, replacement and removal are atomic per definition; snapshots
 * are immutable copies sorted by identifier, so an evaluation always sees one
 * consistent set. Definitions themselves are never mutated: enabling or
 * disabling a strategy means registering a new definition. The registry enforces
 * duplicate detection and the configured limits.</p>
 */
public final class StrategyRegistry {

    private static final Comparator<StrategyDefinition> BY_ID =
            Comparator.comparing(StrategyDefinition::id);

    private final StrategyEngineConfig config;
    private final ConcurrentHashMap<String, StrategyDefinition> definitions =
            new ConcurrentHashMap<>();

    public StrategyRegistry(StrategyEngineConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    public StrategyRegistry(StrategyEngineConfig config,
                            Collection<StrategyDefinition> initial) {
        this(config);
        Objects.requireNonNull(initial, "initial must not be null");
        for (StrategyDefinition definition : initial) {
            register(definition);
        }
    }

    /**
     * Registers a definition, rejecting a duplicate identifier.
     *
     * @throws StrategyValidationException when the identifier already exists or a
     *                                     configured limit is exceeded
     */
    public void register(StrategyDefinition definition) {
        Objects.requireNonNull(definition, "definition must not be null");
        StrategyValidator.validateDefinition(definition, config);
        if (definitions.size() >= config.maxStrategies()) {
            throw new StrategyValidationException(
                    "registry exceeds maxStrategies " + config.maxStrategies());
        }
        StrategyDefinition existing = definitions.putIfAbsent(definition.id(), definition);
        if (existing != null) {
            throw new StrategyValidationException(
                    "duplicate strategy id " + definition.id());
        }
    }

    /**
     * Replaces an existing definition with an immutable one of the same id.
     *
     * @throws StrategyValidationException when no definition carries the id
     */
    public void replace(StrategyDefinition definition) {
        Objects.requireNonNull(definition, "definition must not be null");
        StrategyValidator.validateDefinition(definition, config);
        boolean replaced = definitions.replace(definition.id(), definition) != null;
        if (!replaced) {
            throw new StrategyValidationException(
                    "unknown strategy id " + definition.id());
        }
    }

    /**
     * Removes a definition by id.
     *
     * @return true when a definition was removed
     */
    public boolean unregister(String id) {
        return id != null && definitions.remove(id.trim()) != null;
    }

    public Optional<StrategyDefinition> lookup(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(id.trim()));
    }

    public boolean contains(String id) {
        return id != null && definitions.containsKey(id.trim());
    }

    /**
     * Immutable snapshot of enabled definitions, ordered by identifier.
     */
    public List<StrategyDefinition> enabled() {
        return snapshot().stream().filter(StrategyDefinition::enabled).toList();
    }

    /**
     * Immutable snapshot of disabled definitions, ordered by identifier.
     */
    public List<StrategyDefinition> disabled() {
        return snapshot().stream().filter(definition -> !definition.enabled()).toList();
    }

    /**
     * Immutable snapshot of every definition, ordered by identifier.
     */
    public List<StrategyDefinition> snapshot() {
        return definitions.values().stream().sorted(BY_ID).toList();
    }

    public int size() {
        return definitions.size();
    }

    public StrategyEngineConfig config() {
        return config;
    }
}
