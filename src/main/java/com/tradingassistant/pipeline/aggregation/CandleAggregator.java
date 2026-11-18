package com.tradingassistant.pipeline.aggregation;

import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.Tick;
import com.tradingassistant.pipeline.calendar.MarketCalendar;
import com.tradingassistant.pipeline.exception.PipelineValidationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregates live ticks for one instrument and one interval into completed
 * candles. One instance owns one forming candle; sharing an instance across
 * streams is not supported.
 *
 * <p>Policies, all deterministic and documented:</p>
 * <ul>
 *   <li><strong>Open/high/low/close</strong> follow tick arrival order, not
 *       timestamp order. Accepted ticks are the ones the feed delivered in the
 *       order it delivered them, which is the only ordering a live system
 *       observes.</li>
 *   <li><strong>Volume</strong> accumulates the tick's last traded quantity.
 *       Ticks without a quantity contribute nothing, and a rejected tick never
 *       contributes.</li>
 *   <li><strong>Duplicate and out-of-order ticks</strong> are rejected when the
 *       tick carries a positive sequence number that is not greater than the
 *       last accepted one. A feed without sequence numbers must be deduplicated
 *       upstream; that limitation is explicit rather than guessed.</li>
 *   <li><strong>Late ticks</strong> (a bucket earlier than the forming candle)
 *       are rejected and never restate a closed candle.</li>
 *   <li><strong>Empty intervals</strong> produce no candle. The next accepted
 *       tick closes the forming candle and opens a new bucket; no synthetic
 *       candles are generated.</li>
 *   <li><strong>Future and stale timestamps</strong> are bounded by
 *       {@link AggregationPolicy}. The stale check is disabled by default so
 *       replay uses the same code path as the live feed.</li>
 *   <li><strong>Session gating</strong>: when the alignment strategy is
 *       session-aware, a tick outside the exchange session is rejected. A
 *       pre-open snapshot therefore cannot open the first candle and an
 *       after-hours print cannot extend the last one.</li>
 *   <li><strong>Time source</strong> prefers the exchange timestamp and falls
 *       back to the receipt timestamp when the feed omits it.</li>
 * </ul>
 *
 * <p>Methods are synchronized, so one instance can be offered ticks from a feed
 * thread and flushed from a shutdown thread without losing a candle.</p>
 */
public final class CandleAggregator {

    private final InstrumentKey instrument;
    private final Interval interval;
    private final CandleBucketAligner aligner;
    private final AggregationPolicy policy;
    private final Clock clock;

    private CandleBucket bucket;
    private long lastSequence;
    private Instant lastTickTimestamp;

    public CandleAggregator(
            InstrumentKey instrument,
            Interval interval,
            CandleBucketAligner aligner,
            AggregationPolicy policy,
            Clock clock) {
        this.instrument = Objects.requireNonNull(instrument, "instrument must not be null");
        this.interval = Objects.requireNonNull(interval, "interval must not be null");
        this.aligner = Objects.requireNonNull(aligner, "aligner must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Creates an aggregator whose buckets follow the calendar for the interval.
     */
    public static CandleAggregator create(
            InstrumentKey instrument,
            Interval interval,
            MarketCalendar calendar,
            AggregationPolicy policy,
            Clock clock) {
        return new CandleAggregator(
                instrument,
                interval,
                CandleBucketAligners.forInterval(interval, calendar),
                policy,
                clock);
    }

    public synchronized AggregationResult accept(Tick tick) {
        if (tick == null) {
            throw new PipelineValidationException("tick must not be null");
        }
        if (!instrument.equals(tick.instrument())) {
            return AggregationResult.rejected(AggregationStatus.IGNORED_WRONG_INSTRUMENT);
        }
        BigDecimal price = tick.lastTradedPrice();
        if (price == null) {
            return AggregationResult.rejected(AggregationStatus.IGNORED_MISSING_PRICE);
        }
        long sequence = tick.sequenceNumber();
        if (sequence > 0 && sequence <= lastSequence) {
            return AggregationResult.rejected(AggregationStatus.IGNORED_SEQUENCE);
        }

        Instant timestamp = tick.exchangeTimestamp() != null
                ? tick.exchangeTimestamp()
                : tick.receivedAt();
        Instant now = clock.instant();
        if (timestamp.isAfter(now.plus(policy.futureSkewTolerance()))) {
            return AggregationResult.rejected(AggregationStatus.IGNORED_FUTURE_TIMESTAMP);
        }
        if (policy.staleCheckEnabled() && timestamp.isBefore(now.minus(policy.staleTolerance()))) {
            return AggregationResult.rejected(AggregationStatus.IGNORED_STALE_TIMESTAMP);
        }
        if (!aligner.isTradable(timestamp)) {
            return AggregationResult.rejected(AggregationStatus.IGNORED_OUTSIDE_SESSION);
        }

        Instant bucketStart = aligner.bucketStart(timestamp);
        if (bucket != null && bucketStart.isBefore(bucket.start())) {
            return AggregationResult.rejected(AggregationStatus.IGNORED_LATE_TIMESTAMP);
        }

        long quantity = tick.lastTradedQuantity() == null ? 0L : tick.lastTradedQuantity();
        List<Candle> completed = List.of();
        if (bucket != null && bucketStart.isAfter(bucket.start())) {
            completed = List.of(bucket.toCandle(instrument, interval));
            bucket = null;
        }
        if (bucket == null) {
            bucket = new CandleBucket(bucketStart, price, quantity);
        } else {
            bucket.apply(price, quantity);
        }

        if (sequence > 0) {
            lastSequence = sequence;
        }
        lastTickTimestamp = timestamp;
        return AggregationResult.accepted(bucketStart, completed);
    }

    /**
     * Closes the forming candle, for example at session end or shutdown. Returns
     * empty when no tick has been accepted since the last flush.
     */
    public synchronized Optional<Candle> flush() {
        if (bucket == null) {
            return Optional.empty();
        }
        Candle candle = bucket.toCandle(instrument, interval);
        bucket = null;
        return Optional.of(candle);
    }

    /**
     * Discards the forming candle and the sequence watermark. A reconnecting
     * feed must call this so a restarted provider sequence does not look
     * duplicate.
     */
    public synchronized void reset() {
        bucket = null;
        lastSequence = 0L;
        lastTickTimestamp = null;
    }

    public InstrumentKey instrument() {
        return instrument;
    }

    public Interval interval() {
        return interval;
    }

    public AggregationPolicy policy() {
        return policy;
    }

    public synchronized Optional<Instant> currentBucketStart() {
        return bucket == null ? Optional.empty() : Optional.of(bucket.start());
    }

    public synchronized long lastAcceptedSequence() {
        return lastSequence;
    }

    public synchronized Optional<Instant> lastTickTimestamp() {
        return Optional.ofNullable(lastTickTimestamp);
    }

    /**
     * Nominal bucket length, exposed for diagnostics and scheduling hints.
     */
    public Duration bucketSize() {
        return aligner.bucketSize();
    }
}
