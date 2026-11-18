package com.tradingassistant.pipeline.session;

import com.tradingassistant.indicator.api.Indicator;
import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.pipeline.exception.PipelineValidationException;
import com.tradingassistant.pipeline.exception.SessionExpiredException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One long-lived indicator instance per configured indicator, bound to one
 * instrument, interval and configuration.
 *
 * <p>Indicator instances are stateful and are never shared. Every session owns
 * its own instances, so two instruments or two timeframes cannot corrupt each
 * other's recursive state. All mutation is synchronized on the session;
 * read-only accessors observe the last published snapshot of the fields they
 * need, so a reader never sees a half-applied candle.</p>
 */
public final class IndicatorSession {

    private final IndicatorSessionKey key;
    private final Map<String, Indicator> indicators;
    private final Instant createdAt;

    private volatile Instant lastAccessedAt;
    private volatile IndicatorSessionState state = IndicatorSessionState.ACTIVE;
    private volatile String expiryReason;

    private int candleCount;
    private Instant lastUpdatedAt;
    private Instant lastCandleTimestamp;
    private Map<String, IndicatorResult> latestResults = Map.of();

    IndicatorSession(
            IndicatorSessionKey key,
            Map<String, Indicator> indicators,
            Instant createdAt,
            Instant accessedAt) {
        this.key = Objects.requireNonNull(key, "key must not be null");
        if (indicators == null || indicators.isEmpty()) {
            throw new IllegalArgumentException("A session requires at least one indicator");
        }
        this.indicators = Map.copyOf(new LinkedHashMap<>(indicators));
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.lastAccessedAt = Objects.requireNonNull(accessedAt, "accessedAt must not be null");
    }

    /**
     * Advances every indicator in the session by one candle. The candle must
     * belong to the session's instrument and interval; mixing streams is a
     * programming error, not a runtime condition to absorb.
     */
    public synchronized IndicatorSessionUpdate update(Candle candle, Instant accessedAt) {
        requireActive();
        if (candle == null) {
            throw new PipelineValidationException("candle must not be null");
        }
        if (!key.instrument().equals(candle.instrument())) {
            throw new PipelineValidationException(
                    "Candle instrument " + candle.instrument().segment().code() + ":"
                            + candle.instrument().symbolToken()
                            + " does not belong to session " + describe());
        }
        if (key.interval() != candle.interval()) {
            throw new PipelineValidationException(
                    "Candle interval " + candle.interval()
                            + " does not belong to session " + describe());
        }

        Map<String, IndicatorResult> results = new LinkedHashMap<>();
        for (Map.Entry<String, Indicator> entry : indicators.entrySet()) {
            results.put(entry.getKey(), entry.getValue().update(candle));
        }
        latestResults = Map.copyOf(results);
        candleCount++;
        lastCandleTimestamp = candle.timestamp();
        lastUpdatedAt = Objects.requireNonNull(accessedAt, "accessedAt must not be null");
        lastAccessedAt = accessedAt;
        return new IndicatorSessionUpdate(key, candle, latestResults);
    }

    /**
     * Clears every indicator's recursive state while keeping the session
     * active. The instrument, interval and configuration are unchanged.
     */
    public synchronized void reset(Instant accessedAt) {
        requireActive();
        indicators.values().forEach(Indicator::reset);
        latestResults = Map.of();
        candleCount = 0;
        lastUpdatedAt = null;
        lastCandleTimestamp = null;
        lastAccessedAt = Objects.requireNonNull(accessedAt, "accessedAt must not be null");
    }

    /**
     * Latest observation per indicator, or an empty map before the first candle.
     */
    public Map<String, IndicatorResult> results() {
        return latestResults;
    }

    public Optional<IndicatorResult> result(String indicatorType) {
        if (indicatorType == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(latestResults.get(indicatorType.trim().toUpperCase(java.util.Locale.ROOT)));
    }

    public IndicatorSessionKey key() {
        return key;
    }

    public IndicatorSessionState state() {
        return state;
    }

    public boolean isActive() {
        return state == IndicatorSessionState.ACTIVE;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastAccessedAt() {
        return lastAccessedAt;
    }

    public Optional<Instant> lastUpdatedAt() {
        return Optional.ofNullable(lastUpdatedAt);
    }

    public Optional<Instant> lastCandleTimestamp() {
        return Optional.ofNullable(lastCandleTimestamp);
    }

    public synchronized int candleCount() {
        return candleCount;
    }

    public Set<String> indicatorTypes() {
        return indicators.keySet();
    }

    public int indicatorCount() {
        return indicators.size();
    }

    public Optional<String> expiryReason() {
        return Optional.ofNullable(expiryReason);
    }

    public synchronized IndicatorSessionSnapshot snapshot() {
        return new IndicatorSessionSnapshot(
                key,
                indicators.keySet(),
                createdAt,
                lastUpdatedAt,
                lastCandleTimestamp,
                candleCount,
                state);
    }

    synchronized void touch(Instant accessedAt) {
        if (state == IndicatorSessionState.ACTIVE && accessedAt != null) {
            lastAccessedAt = accessedAt;
        }
    }

    /**
     * Expires the session only if it is still idle. The check is repeated under
     * the session lock, so an update that starts after the registry's scan is
     * either observed as a fresh access or rejected by the terminal state.
     */
    synchronized boolean expireIfIdle(Instant idleBefore, String reason) {
        if (state != IndicatorSessionState.ACTIVE) {
            return false;
        }
        if (!lastAccessedAt.isBefore(idleBefore)) {
            return false;
        }
        state = IndicatorSessionState.EXPIRED;
        expiryReason = reason;
        return true;
    }

    synchronized void markExpired(String reason) {
        if (state == IndicatorSessionState.ACTIVE) {
            state = IndicatorSessionState.EXPIRED;
            expiryReason = reason;
        }
    }

    private void requireActive() {
        if (state != IndicatorSessionState.ACTIVE) {
            throw new SessionExpiredException(
                    "Indicator session has expired: " + describe()
                            + (expiryReason == null ? "" : " (" + expiryReason + ")"));
        }
    }

    private String describe() {
        return key.instrument().segment().code() + ":" + key.instrument().symbolToken()
                + "/" + key.interval();
    }
}
