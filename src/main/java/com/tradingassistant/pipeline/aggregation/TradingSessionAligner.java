package com.tradingassistant.pipeline.aggregation;

import com.tradingassistant.pipeline.calendar.MarketCalendar;
import com.tradingassistant.pipeline.calendar.TradingSession;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Intraday buckets are anchored to each trading day's session open, so a
 * five-minute series starts at 09:15 rather than at a wall-clock multiple.
 *
 * <p>The aligner is session-aware: {@link #isTradable(Instant)} is true only
 * inside a calendar session, so a pre-open snapshot or an after-hours print is
 * rejected by the aggregator instead of being clamped into the first or last
 * candle of the day. A feed that must capture the post-close auction subscribes
 * with a calendar whose window includes that window.</p>
 *
 * <p>On a date without a session (a replayed abnormal day) the regular window
 * for that date defines the grid, which keeps bucket boundaries stable and
 * deterministic.</p>
 */
public final class TradingSessionAligner implements CandleBucketAligner {

    private final MarketCalendar calendar;
    private final Duration step;

    public TradingSessionAligner(MarketCalendar calendar, Duration step) {
        this.calendar = Objects.requireNonNull(calendar, "calendar must not be null");
        if (step == null || step.isZero() || step.isNegative()) {
            throw new IllegalArgumentException("step must be positive");
        }
        this.step = step;
    }

    @Override
    public Instant bucketStart(Instant timestamp) {
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp must not be null");
        }
        LocalDate date = calendar.marketDate(timestamp);
        TradingSession session = calendar.sessionOn(date).orElse(null);
        Instant open = session != null
                ? session.open()
                : calendar.regularWindow().start(date, calendar.zone());
        return gridStart(open, timestamp);
    }

    @Override
    public boolean isTradable(Instant timestamp) {
        return timestamp != null && calendar.currentSession(timestamp).isPresent();
    }

    @Override
    public Duration bucketSize() {
        return step;
    }

    private Instant gridStart(Instant open, Instant timestamp) {
        long stepNanos = step.toNanos();
        long offsetNanos = Duration.between(open, timestamp).toNanos();
        long index = Math.floorDiv(offsetNanos, stepNanos);
        return open.plusNanos(index * stepNanos);
    }
}
