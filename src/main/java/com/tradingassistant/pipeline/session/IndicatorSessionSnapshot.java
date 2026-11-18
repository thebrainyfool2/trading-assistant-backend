package com.tradingassistant.pipeline.session;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Portable description of a session, produced for diagnostics and for the
 * future persistence hook. It intentionally carries no indicator arithmetic
 * state: restoring a session recreates the binding, and a replay of candles
 * rebuilds the recursive state, which keeps recovery free of a serialization
 * format that no approved phase has specified.
 *
 * @param session             session identity
 * @param indicatorTypes      indicator types the session owns
 * @param createdAt           creation time
 * @param lastUpdatedAt       last successful update, null before the first candle
 * @param lastCandleTimestamp timestamp of the last candle, null before the first candle
 * @param candleCount         candles processed since creation or the last reset
 * @param state               lifecycle state at snapshot time
 */
public record IndicatorSessionSnapshot(
        IndicatorSessionKey session,
        Set<String> indicatorTypes,
        Instant createdAt,
        Instant lastUpdatedAt,
        Instant lastCandleTimestamp,
        int candleCount,
        IndicatorSessionState state) {

    public IndicatorSessionSnapshot {
        session = Objects.requireNonNull(session, "session must not be null");
        indicatorTypes = indicatorTypes == null ? Set.of() : Set.copyOf(indicatorTypes);
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        state = Objects.requireNonNull(state, "state must not be null");
        if (candleCount < 0) {
            throw new IllegalArgumentException("candleCount must not be negative");
        }
    }
}
