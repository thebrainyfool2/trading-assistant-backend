package com.tradingassistant.pipeline.session;

import com.tradingassistant.indicator.api.Indicator;
import com.tradingassistant.indicator.engine.IndicatorEngine;
import com.tradingassistant.pipeline.exception.PipelineValidationException;
import com.tradingassistant.pipeline.exception.SessionLimitExceededException;
import com.tradingassistant.pipeline.exception.SessionNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns every long-lived indicator session in the running pipeline.
 *
 * <p>Sessions are created lazily on first use, keyed by instrument, interval and
 * canonicalized configuration. The registry never shares indicator instances and
 * never evicts silently: a session leaves only through an explicit removal, an
 * idle sweep, or shutdown, and each transition is reported to the configured
 * {@link SessionLifecycleListener}.</p>
 *
 * <p>Read-only lookups ({@link #find(IndicatorSessionKey)}) do not extend a
 * session's idle deadline; only real work does. That keeps a diagnostic request
 * from keeping a dead instrument alive for another idle window.</p>
 */
public final class IndicatorSessionRegistry {

    private final IndicatorEngine engine;
    private final SessionRegistryPolicy policy;
    private final Clock clock;
    private final SessionLifecycleListener listener;
    private final ConcurrentHashMap<IndicatorSessionKey, IndicatorSession> sessions = new ConcurrentHashMap<>();

    public IndicatorSessionRegistry(
            IndicatorEngine engine,
            SessionRegistryPolicy policy,
            Clock clock,
            SessionLifecycleListener listener) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.listener = listener == null ? SessionLifecycleListener.noop() : listener;
    }

    /**
     * Returns the session for the key, creating it and its indicator instances
     * when absent. Concurrent callers for one key observe the same session.
     */
    public IndicatorSession getOrCreate(IndicatorSessionKey key, Set<String> indicatorTypes) {
        Objects.requireNonNull(key, "key must not be null");
        LinkedHashSet<String> types = requireTypes(indicatorTypes);
        Instant now = clock.instant();

        IndicatorSession existing = sessions.get(key);
        if (existing != null) {
            existing.touch(now);
            return existing;
        }

        AtomicReference<IndicatorSession> created = new AtomicReference<>();
        IndicatorSession session = sessions.computeIfAbsent(key, requested -> {
            if (sessions.mappingCount() >= policy.maxSessions()) {
                throw new SessionLimitExceededException(
                        "Session limit reached (" + policy.maxSessions() + "); expire inactive sessions first");
            }
            IndicatorSession fresh = new IndicatorSession(
                    requested, createIndicators(requested, types), now, now);
            created.set(fresh);
            return fresh;
        });
        session.touch(now);

        IndicatorSession fresh = created.get();
        if (fresh != null) {
            listener.onSessionStarted(fresh);
        }
        return session;
    }

    public Optional<IndicatorSession> find(IndicatorSessionKey key) {
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessions.get(key));
    }

    public IndicatorSession require(IndicatorSessionKey key) {
        return find(key).orElseThrow(() -> new SessionNotFoundException(
                "No indicator session exists for " + describe(key)));
    }

    /**
     * Clears recursive indicator state while keeping the session registered.
     */
    public IndicatorSession reset(IndicatorSessionKey key) {
        IndicatorSession session = require(key);
        session.reset(clock.instant());
        return session;
    }

    /**
     * Expires and unregisters one session.
     */
    public boolean remove(IndicatorSessionKey key, String reason) {
        if (key == null) {
            return false;
        }
        IndicatorSession session = sessions.get(key);
        if (session == null) {
            return false;
        }
        session.markExpired(reason);
        sessions.remove(key, session);
        listener.onSessionExpired(session, reason);
        return true;
    }

    /**
     * Expires sessions untouched for longer than the policy idle timeout.
     *
     * @return keys that expired during this sweep, in encounter order
     */
    public List<IndicatorSessionKey> expireIdle() {
        if (!policy.idleExpiryEnabled()) {
            return List.of();
        }
        Instant idleBefore = clock.instant().minus(policy.idleTimeout());
        return expireWhere(session -> session.expireIfIdle(idleBefore, "idle"), "idle");
    }

    /**
     * Expires every session, used by a controlled shutdown.
     */
    public List<IndicatorSessionKey> expireAll(String reason) {
        return expireWhere(session -> {
            session.markExpired(reason);
            return true;
        }, reason);
    }

    public int size() {
        return sessions.size();
    }

    public Set<IndicatorSessionKey> keys() {
        return Set.copyOf(sessions.keySet());
    }

    public List<IndicatorSessionSnapshot> snapshots() {
        List<IndicatorSessionSnapshot> snapshots = new ArrayList<>(sessions.size());
        for (IndicatorSession session : sessions.values()) {
            snapshots.add(session.snapshot());
        }
        return List.copyOf(snapshots);
    }

    /**
     * Recovery hook: recreates an active session binding from a snapshot.
     * Indicator arithmetic state is rebuilt by replaying candles, because no
     * approved phase defines a persisted representation of recursive state.
     */
    public IndicatorSession restore(IndicatorSessionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        if (snapshot.state() != IndicatorSessionState.ACTIVE) {
            throw new PipelineValidationException("Only an active session snapshot can be restored");
        }
        return getOrCreate(snapshot.session(), snapshot.indicatorTypes());
    }

    /**
     * Validates indicator types against the engine without creating a session.
     */
    public LinkedHashSet<String> requireTypes(Set<String> indicatorTypes) {
        if (indicatorTypes == null || indicatorTypes.isEmpty()) {
            throw new PipelineValidationException("At least one indicator type is required");
        }
        TreeSet<String> sorted = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String type : indicatorTypes) {
            if (type == null || type.isBlank()) {
                throw new PipelineValidationException("Indicator types must not be blank");
            }
            if (!engine.supports(type)) {
                throw new PipelineValidationException("Unsupported indicator: " + type);
            }
            sorted.add(type.trim());
        }
        if (sorted.isEmpty()) {
            throw new PipelineValidationException("At least one indicator type is required");
        }
        return new LinkedHashSet<>(sorted);
    }

    public SessionRegistryPolicy policy() {
        return policy;
    }

    private Map<String, Indicator> createIndicators(IndicatorSessionKey key, Set<String> types) {
        Map<String, Indicator> created = new LinkedHashMap<>();
        for (String type : types) {
            Indicator indicator = engine.create(type, key.config());
            created.put(indicator.name(), indicator);
        }
        return created;
    }

    private List<IndicatorSessionKey> expireWhere(java.util.function.Predicate<IndicatorSession> condition,
                                                  String reason) {
        List<IndicatorSession> expired = new ArrayList<>();
        for (IndicatorSession session : sessions.values()) {
            if (condition.test(session) && sessions.remove(session.key(), session)) {
                expired.add(session);
            }
        }
        List<IndicatorSessionKey> keys = new ArrayList<>(expired.size());
        for (IndicatorSession session : expired) {
            keys.add(session.key());
            listener.onSessionExpired(session, reason);
        }
        return List.copyOf(keys);
    }

    private static String describe(IndicatorSessionKey key) {
        return key == null
                ? "<null>"
                : key.instrument().segment().code() + ":" + key.instrument().symbolToken()
                        + "/" + key.interval();
    }
}
