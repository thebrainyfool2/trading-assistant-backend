package com.tradingassistant.pipeline.aggregation;

import com.tradingassistant.market.domain.Candle;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Immutable outcome of offering one tick to an aggregator. A single tick can
 * complete at most one candle because empty intervals are never filled, so the
 * list is normally empty or contains one completed candle.
 *
 * @param status           acceptance outcome
 * @param completedCandles candles closed by this tick, oldest first
 * @param bucketStart      bucket that received the tick; null when it was rejected
 */
public record AggregationResult(AggregationStatus status, List<Candle> completedCandles, Instant bucketStart) {

    public AggregationResult {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        completedCandles = completedCandles == null ? List.of() : List.copyOf(completedCandles);
        if (status == AggregationStatus.ACCEPTED) {
            if (bucketStart == null) {
                throw new IllegalArgumentException("An accepted tick requires bucketStart");
            }
        } else if (bucketStart != null) {
            throw new IllegalArgumentException("Rejected ticks cannot report an active bucket");
        }
    }

    static AggregationResult rejected(AggregationStatus status) {
        return new AggregationResult(status, List.of(), null);
    }

    static AggregationResult accepted(Instant bucketStart, List<Candle> completedCandles) {
        return new AggregationResult(AggregationStatus.ACCEPTED, completedCandles, bucketStart);
    }

    public boolean isAccepted() {
        return status == AggregationStatus.ACCEPTED;
    }

    public boolean hasCompletedCandles() {
        return !completedCandles.isEmpty();
    }

    public Optional<Instant> currentBucketStart() {
        return Optional.ofNullable(bucketStart);
    }
}
