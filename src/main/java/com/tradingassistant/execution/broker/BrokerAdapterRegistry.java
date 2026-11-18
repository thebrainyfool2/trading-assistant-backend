package com.tradingassistant.execution.broker;

import com.tradingassistant.execution.exception.ExecutionValidationException;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of broker adapters.
 *
 * <p>The configuration names the adapter to use; the registry resolves that name.
 * Registration, replacement and removal are atomic, snapshots are immutable and
 * duplicates are rejected.</p>
 */
public final class BrokerAdapterRegistry {

    private static final Comparator<BrokerAdapter> BY_NAME =
            Comparator.comparing(BrokerAdapter::brokerName);

    private final ConcurrentHashMap<String, BrokerAdapter> adapters = new ConcurrentHashMap<>();

    public BrokerAdapterRegistry() {
    }

    public BrokerAdapterRegistry(Collection<BrokerAdapter> initial) {
        Objects.requireNonNull(initial, "initial must not be null");
        for (BrokerAdapter adapter : initial) {
            register(adapter);
        }
    }

    public void register(BrokerAdapter adapter) {
        requireAdapter(adapter);
        BrokerAdapter existing = adapters.putIfAbsent(adapter.brokerName().trim(), adapter);
        if (existing != null) {
            throw new ExecutionValidationException(
                    "duplicate broker adapter " + adapter.brokerName());
        }
    }

    public void replace(BrokerAdapter adapter) {
        requireAdapter(adapter);
        boolean replaced = adapters.replace(adapter.brokerName().trim(), adapter) != null;
        if (!replaced) {
            throw new ExecutionValidationException(
                    "unknown broker adapter " + adapter.brokerName());
        }
    }

    public boolean unregister(String brokerName) {
        return brokerName != null && adapters.remove(brokerName.trim()) != null;
    }

    public Optional<BrokerAdapter> lookup(String brokerName) {
        if (brokerName == null || brokerName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(adapters.get(brokerName.trim()));
    }

    /**
     * Resolves the configured adapter.
     *
     * @throws ExecutionValidationException when no adapter carries the name
     */
    public BrokerAdapter select(String brokerName) {
        return lookup(brokerName).orElseThrow(() -> new ExecutionValidationException(
                "no broker adapter registered for " + brokerName));
    }

    public List<BrokerAdapter> snapshot() {
        return adapters.values().stream().sorted(BY_NAME).toList();
    }

    public int size() {
        return adapters.size();
    }

    private void requireAdapter(BrokerAdapter adapter) {
        if (adapter == null) {
            throw new ExecutionValidationException("broker adapter must not be null");
        }
        if (adapter.brokerName() == null || adapter.brokerName().isBlank()) {
            throw new ExecutionValidationException("broker name must not be blank");
        }
    }
}
