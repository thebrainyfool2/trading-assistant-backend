package com.tradingassistant.pipeline.aggregation;

import java.time.Duration;
import java.time.Instant;

/**
 * Arithmetic bucket grid anchored at an explicit instant. Used when the caller
 * already knows the grid origin, for example the session open of a historical
 * backfill range.
 */
public final class FixedStepAligner implements CandleBucketAligner {

    private final Instant anchor;
    private final Duration step;

    public FixedStepAligner(Instant anchor, Duration step) {
        if (anchor == null) {
            throw new IllegalArgumentException("anchor must not be null");
        }
        if (step == null || step.isZero() || step.isNegative()) {
            throw new IllegalArgumentException("step must be positive");
        }
        this.anchor = anchor;
        this.step = step;
    }

    @Override
    public Instant bucketStart(Instant timestamp) {
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp must not be null");
        }
        long stepNanos = step.toNanos();
        long offsetNanos = Duration.between(anchor, timestamp).toNanos();
        long index = Math.floorDiv(offsetNanos, stepNanos);
        return anchor.plusNanos(index * stepNanos);
    }

    @Override
    public Duration bucketSize() {
        return step;
    }
}
