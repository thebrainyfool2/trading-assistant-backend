package com.tradingassistant.pipeline.aggregation;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Daily buckets start at midnight in the market time zone. This matches the
 * timestamp convention of daily provider candles, so a live daily candle and a
 * historical daily candle describe the same date.
 */
public final class MarketDateAligner implements CandleBucketAligner {

    private final ZoneId zone;

    public MarketDateAligner(ZoneId zone) {
        this.zone = Objects.requireNonNull(zone, "zone must not be null");
    }

    @Override
    public Instant bucketStart(Instant timestamp) {
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp must not be null");
        }
        LocalDate date = timestamp.atZone(zone).toLocalDate();
        return date.atStartOfDay(zone).toInstant();
    }

    @Override
    public Duration bucketSize() {
        return Duration.ofDays(1);
    }
}
