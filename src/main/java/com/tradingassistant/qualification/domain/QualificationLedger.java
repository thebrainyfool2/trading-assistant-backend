package com.tradingassistant.qualification.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded, thread-safe memory of qualifications already reported.
 *
 * <p>The ledger is deliberately bounded: it keeps the most recent qualified keys
 * and evicts the oldest when the capacity is reached, so a long-running process
 * cannot accumulate one entry per candle. It is not a database, not an audit
 * trail and not persistent state; it exists only to recognise a repeated
 * qualification of the same strategy, instrument and instant.</p>
 *
 * <p>The ledger is a collaborator, not a model: rules stay immutable because they
 * hold a final reference to it, and only the service that owns it mutates it.</p>
 */
public final class QualificationLedger {

    /** Default number of remembered qualified keys. */
    public static final int DEFAULT_CAPACITY = 1_024;

    private final int capacity;
    private final Map<QualificationKey, Boolean> recorded;

    public QualificationLedger() {
        this(DEFAULT_CAPACITY);
    }

    public QualificationLedger(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        this.capacity = capacity;
        this.recorded = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<QualificationKey, Boolean> eldest) {
                return size() > QualificationLedger.this.capacity;
            }
        });
    }

    /**
     * Records a qualification key.
     */
    public void record(QualificationKey key) {
        Objects.requireNonNull(key, "key must not be null");
        recorded.put(key, Boolean.TRUE);
    }

    /**
     * True when this key was already qualified and is still remembered.
     */
    public boolean contains(QualificationKey key) {
        return key != null && recorded.containsKey(key);
    }

    public int size() {
        return recorded.size();
    }

    public int capacity() {
        return capacity;
    }

    public void clear() {
        recorded.clear();
    }
}
