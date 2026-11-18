package com.tradingassistant.pipeline.aggregation;

import java.time.Duration;
import java.util.Optional;

/**
 * Acceptance tolerances for live tick aggregation.
 *
 * <p>{@code futureSkewTolerance} protects the forming candle from a feed whose
 * clock runs ahead of the application clock. {@code staleTolerance} rejects
 * delayed ticks; a zero tolerance disables the check, which is the default
 * because historical replay legitimately feeds old timestamps through the same
 * aggregator.</p>
 *
 * @param futureSkewTolerance   how far ahead of the application clock a tick may be
 * @param staleTolerance        maximum age of a tick; {@link Duration#ZERO} disables the check
 */
public record AggregationPolicy(Duration futureSkewTolerance, Duration staleTolerance) {

    public AggregationPolicy {
        if (futureSkewTolerance == null || futureSkewTolerance.isNegative()) {
            throw new IllegalArgumentException("futureSkewTolerance must be zero or positive");
        }
        if (staleTolerance == null || staleTolerance.isNegative()) {
            throw new IllegalArgumentException("staleTolerance must be zero or positive");
        }
    }

    public static AggregationPolicy defaults() {
        return new AggregationPolicy(Duration.ofSeconds(5), Duration.ZERO);
    }

    /**
     * Defaults with a bounded tick age, intended for live feeds that can deliver
     * late batches.
     */
    public static AggregationPolicy live(Duration staleTolerance) {
        return new AggregationPolicy(Duration.ofSeconds(5), staleTolerance);
    }

    public boolean staleCheckEnabled() {
        return !staleTolerance.isZero();
    }

    public Optional<Duration> staleToleranceIfEnabled() {
        return staleCheckEnabled() ? Optional.of(staleTolerance) : Optional.empty();
    }
}
