package com.tradingassistant.pipeline;

import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.pipeline.session.IndicatorSessionKey;
import com.tradingassistant.pipeline.session.IndicatorSessionRegistry;
import com.tradingassistant.pipeline.session.IndicatorSessionSnapshot;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Orchestrates session lifecycle: start, drain, idle sweep and shutdown.
 *
 * <p>Deliberately synchronous. Phase 5 has no approved scheduler, so the manager
 * never starts a thread: an operator, a request or a later phase's scheduler
 * calls {@link #sweep()} at whatever cadence is appropriate. Shutdown order is
 * fixed and therefore testable: forming candles are flushed first, then every
 * session expires, so no candle is lost between the two steps.</p>
 */
public final class MarketSessionLifecycleManager implements AutoCloseable {

    public static final String REASON_CLOSED = "closed";
    public static final String REASON_SHUTDOWN = "shutdown";

    private final MarketProcessingPipeline pipeline;
    private final IndicatorSessionRegistry registry;
    private final Clock clock;
    private final Object lifecycleLock = new Object();

    private volatile boolean started;
    private volatile Instant startedAt;

    public MarketSessionLifecycleManager(
            MarketProcessingPipeline pipeline,
            IndicatorSessionRegistry registry,
            Clock clock) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline must not be null");
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Marks the pipeline as running. Idempotent.
     */
    public void start() {
        synchronized (lifecycleLock) {
            if (started) {
                return;
            }
            started = true;
            startedAt = clock.instant();
        }
    }

    public boolean isStarted() {
        return started;
    }

    public Optional<Instant> startedAt() {
        return Optional.ofNullable(startedAt);
    }

    /**
     * Expires sessions that exceeded the registry idle timeout.
     *
     * @return number of sessions expired by this sweep
     */
    public int sweep() {
        return registry.expireIdle().size();
    }

    /**
     * Publishes every forming candle as a completed candle.
     */
    public List<Candle> drain() {
        return pipeline.flushAll();
    }

    /**
     * Ends one session without stopping the pipeline.
     */
    public boolean closeSession(IndicatorSessionKey key) {
        return registry.remove(key, REASON_CLOSED);
    }

    /**
     * Flushes forming candles and expires every session. Idempotent.
     */
    public List<IndicatorSessionKey> shutdown() {
        synchronized (lifecycleLock) {
            pipeline.flushAll();
            List<IndicatorSessionKey> expired = registry.expireAll(REASON_SHUTDOWN);
            started = false;
            return expired;
        }
    }

    /**
     * Recovery hook: an immutable snapshot of every active session.
     */
    public List<IndicatorSessionSnapshot> snapshots() {
        return registry.snapshots();
    }

    /**
     * Restores session bindings from snapshots without starting the pipeline.
     * Candle replay rebuilds indicator arithmetic state.
     */
    public int restore(List<IndicatorSessionSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return 0;
        }
        int restored = 0;
        for (IndicatorSessionSnapshot snapshot : snapshots) {
            registry.restore(snapshot);
            restored++;
        }
        return restored;
    }

    @Override
    public void close() {
        shutdown();
    }
}
