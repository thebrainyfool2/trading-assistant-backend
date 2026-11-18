package com.tradingassistant.pipeline.aggregation;

import java.time.Duration;
import java.time.Instant;

/**
 * Maps a tick timestamp onto the start of its candle bucket.
 *
 * <p>Alignment is a strategy because the correct grid depends on the calendar
 * and the interval: intraday candles are anchored to the exchange session open
 * while daily candles follow the market date. Keeping the grid explicit means
 * aggregated candles can be compared with provider history without an
 * undocumented timestamp convention.</p>
 */
public interface CandleBucketAligner {

    /**
     * Start of the bucket that contains the timestamp.
     */
    Instant bucketStart(Instant timestamp);

    /**
     * Nominal bucket length, used to advance to the next bucket.
     */
    Duration bucketSize();

    /**
     * Whether a timestamp may contribute to a candle. Session-aware aligners
     * return false outside the exchange session so aggregation never invents a
     * candle after the close; grids without a session concept accept anything.
     */
    default boolean isTradable(Instant timestamp) {
        return true;
    }

    /**
     * Next bucket start after the given bucket. Falls back to a fixed step when
     * an alignment strategy would not advance (for example at a session
     * boundary), so rollover always terminates.
     */
    default Instant nextBucketStart(Instant currentBucketStart) {
        if (currentBucketStart == null) {
            throw new IllegalArgumentException("currentBucketStart must not be null");
        }
        Instant candidate = bucketStart(currentBucketStart.plus(bucketSize()));
        return candidate.isAfter(currentBucketStart) ? candidate : currentBucketStart.plus(bucketSize());
    }
}
