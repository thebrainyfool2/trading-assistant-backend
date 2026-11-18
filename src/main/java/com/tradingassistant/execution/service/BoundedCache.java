package com.tradingassistant.execution.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Bounded, thread-safe cache with least-recently-used eviction.
 *
 * <p>Execution keeps only the inputs of the most recent pipeline instants. The bound
 * is fixed at construction, so a long-running process cannot accumulate state.</p>
 *
 * @param <K> key type
 * @param <V> value type
 */
public final class BoundedCache<K, V> {

    private final int capacity;
    private final Map<K, V> values;

    public BoundedCache(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        this.capacity = capacity;
        this.values = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > BoundedCache.this.capacity;
            }
        });
    }

    public void put(K key, V value) {
        if (key == null || value == null) {
            return;
        }
        values.put(key, value);
    }

    public Optional<V> get(K key) {
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(values.get(key));
    }

    public List<V> snapshot() {
        synchronized (values) {
            return List.copyOf(new ArrayList<>(values.values()));
        }
    }

    public int size() {
        return values.size();
    }

    public int capacity() {
        return capacity;
    }

    public void clear() {
        values.clear();
    }
}
