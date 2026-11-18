package com.tradingassistant.pipeline.aggregation;

import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.market.domain.Interval;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static com.tradingassistant.pipeline.PipelineTestFixtures.NSE_KEY;
import static com.tradingassistant.pipeline.PipelineTestFixtures.SECOND_KEY;
import static com.tradingassistant.pipeline.PipelineTestFixtures.clockAt;
import static com.tradingassistant.pipeline.PipelineTestFixtures.ist;
import static com.tradingassistant.pipeline.PipelineTestFixtures.nseCalendar;
import static com.tradingassistant.pipeline.PipelineTestFixtures.tick;
import static com.tradingassistant.pipeline.PipelineTestFixtures.tickWithoutPrice;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CandleAggregatorTest {

    @Test
    void firstTickStartsABucketAndTheNextIntervalClosesTheCandle() {
        CandleAggregator aggregator = aggregator(Interval.ONE_MINUTE, clockAt("2026-11-18T09:16:10"));

        AggregationResult first = aggregator.accept(tick(NSE_KEY, 1L, "2026-11-18T09:15:05", "100"));
        assertThat(first.isAccepted()).isTrue();
        assertThat(first.completedCandles()).isEmpty();
        assertThat(first.currentBucketStart()).contains(ist("2026-11-18T09:15:00"));

        aggregator.accept(tick(NSE_KEY, 2L, "2026-11-18T09:15:30", "102"));
        AggregationResult rollover = aggregator.accept(tick(NSE_KEY, 3L, "2026-11-18T09:16:01", "101"));

        Candle completed = rollover.completedCandles().getFirst();
        assertThat(completed.instrument()).isEqualTo(NSE_KEY);
        assertThat(completed.interval()).isEqualTo(Interval.ONE_MINUTE);
        assertThat(completed.timestamp()).isEqualTo(ist("2026-11-18T09:15:00"));
        assertThat(completed.open()).isEqualByComparingTo("100");
        assertThat(completed.high()).isEqualByComparingTo("102");
        assertThat(completed.low()).isEqualByComparingTo("100");
        assertThat(completed.close()).isEqualByComparingTo("102");
        assertThat(completed.volume()).isEqualTo(20L);

        Candle forming = aggregator.flush().orElseThrow();
        assertThat(forming.timestamp()).isEqualTo(ist("2026-11-18T09:16:00"));
        assertThat(forming.open()).isEqualByComparingTo("101");
        assertThat(forming.close()).isEqualByComparingTo("101");
        assertThat(forming.volume()).isEqualTo(10L);
        assertThat(aggregator.flush()).isEmpty();
    }

    @Test
    void everyIntervalAlignsToTheSessionOpenOrTheMarketDate() {
        Clock clock = clockAt("2026-11-18T09:23:00");
        Map<Interval, Instant> expected = Map.of(
                Interval.ONE_MINUTE, ist("2026-11-18T09:22:00"),
                Interval.THREE_MINUTE, ist("2026-11-18T09:21:00"),
                Interval.FIVE_MINUTE, ist("2026-11-18T09:20:00"),
                Interval.TEN_MINUTE, ist("2026-11-18T09:15:00"),
                Interval.FIFTEEN_MINUTE, ist("2026-11-18T09:15:00"),
                Interval.THIRTY_MINUTE, ist("2026-11-18T09:15:00"),
                Interval.ONE_HOUR, ist("2026-11-18T09:15:00"),
                Interval.ONE_DAY, ist("2026-11-18T00:00:00"));

        for (Map.Entry<Interval, Instant> entry : expected.entrySet()) {
            CandleAggregator aggregator = aggregator(entry.getKey(), clock);
            aggregator.accept(tick(NSE_KEY, 1L, "2026-11-18T09:22:30", "100"));
            assertThat(aggregator.currentBucketStart())
                    .as(entry.getKey().name())
                    .contains(entry.getValue());
        }
    }

    @Test
    void dailyBucketsFollowTheMarketDate() {
        CandleAggregator aggregator = aggregator(Interval.ONE_DAY, clockAt("2026-11-19T10:00:00"));

        aggregator.accept(tick(NSE_KEY, 1L, "2026-11-18T10:00:00", "100"));
        AggregationResult rollover = aggregator.accept(tick(NSE_KEY, 2L, "2026-11-19T10:00:00", "105"));

        Candle completed = rollover.completedCandles().getFirst();
        assertThat(completed.timestamp()).isEqualTo(ist("2026-11-18T00:00:00"));
        assertThat(aggregator.currentBucketStart()).contains(ist("2026-11-19T00:00:00"));
    }

    @Test
    void duplicateAndOutOfOrderSequencesAreRejectedWithoutDoubleCountingVolume() {
        CandleAggregator aggregator = aggregator(Interval.ONE_MINUTE, clockAt("2026-11-18T09:16:00"));

        assertThat(aggregator.accept(tick(NSE_KEY, 5L, "2026-11-18T09:15:05", "100")).isAccepted()).isTrue();
        assertThat(aggregator.accept(tick(NSE_KEY, 5L, "2026-11-18T09:15:10", "999")).status())
                .isEqualTo(AggregationStatus.IGNORED_SEQUENCE);
        assertThat(aggregator.accept(tick(NSE_KEY, 4L, "2026-11-18T09:15:20", "999")).status())
                .isEqualTo(AggregationStatus.IGNORED_SEQUENCE);

        Candle candle = aggregator.flush().orElseThrow();
        assertThat(candle.close()).isEqualByComparingTo("100");
        assertThat(candle.high()).isEqualByComparingTo("100");
        assertThat(candle.volume()).isEqualTo(10L);
    }

    @Test
    void lateTicksNeverRestateAClosedBucket() {
        CandleAggregator aggregator = aggregator(Interval.FIVE_MINUTE, clockAt("2026-11-18T09:21:00"));

        aggregator.accept(tick(NSE_KEY, 1L, "2026-11-18T09:20:05", "100"));
        AggregationResult late = aggregator.accept(tick(NSE_KEY, 2L, "2026-11-18T09:15:30", "999"));

        assertThat(late.status()).isEqualTo(AggregationStatus.IGNORED_LATE_TIMESTAMP);
        assertThat(late.currentBucketStart()).isEmpty();
        assertThat(aggregator.currentBucketStart()).contains(ist("2026-11-18T09:20:00"));
        assertThat(aggregator.flush().orElseThrow().high()).isEqualByComparingTo("100");
    }

    @Test
    void futureTimestampsAreBoundedByTheTolerance() {
        CandleAggregator aggregator = aggregator(Interval.ONE_MINUTE, clockAt("2026-11-18T09:15:00"));

        assertThat(aggregator.accept(tick(NSE_KEY, 1L, ist("2026-11-18T09:15:05"), "100", 10L)).isAccepted())
                .isTrue();
        assertThat(aggregator.accept(tick(NSE_KEY, 2L, ist("2026-11-18T09:15:06"), "100", 10L)).status())
                .isEqualTo(AggregationStatus.IGNORED_FUTURE_TIMESTAMP);
    }

    @Test
    void staleTicksAreRejectedOnlyWhenThePolicyEnablesTheCheck() {
        Clock clock = clockAt("2026-11-18T09:30:00");
        CandleAggregator live = new CandleAggregator(
                NSE_KEY, Interval.ONE_MINUTE,
                CandleBucketAligners.forInterval(Interval.ONE_MINUTE, nseCalendar()),
                AggregationPolicy.live(Duration.ofMinutes(5)), clock);
        CandleAggregator relaxing = aggregator(Interval.ONE_MINUTE, clock);

        assertThat(live.accept(tick(NSE_KEY, 1L, "2026-11-18T09:20:00", "100")).status())
                .isEqualTo(AggregationStatus.IGNORED_STALE_TIMESTAMP);
        assertThat(relaxing.accept(tick(NSE_KEY, 1L, "2026-11-18T09:20:00", "100")).isAccepted()).isTrue();
        assertThat(relaxing.policy().staleCheckEnabled()).isFalse();
        assertThat(live.policy().staleToleranceIfEnabled()).contains(Duration.ofMinutes(5));
    }

    @Test
    void wrongInstrumentAndMissingPriceAreRejected() {
        CandleAggregator aggregator = aggregator(Interval.ONE_MINUTE, clockAt("2026-11-18T09:16:00"));

        assertThat(aggregator.accept(tick(SECOND_KEY, 1L, "2026-11-18T09:15:05", "100")).status())
                .isEqualTo(AggregationStatus.IGNORED_WRONG_INSTRUMENT);
        assertThat(aggregator.accept(tickWithoutPrice(NSE_KEY, 2L, ist("2026-11-18T09:15:06"))).status())
                .isEqualTo(AggregationStatus.IGNORED_MISSING_PRICE);
        assertThat(aggregator.flush()).isEmpty();
    }

    @Test
    void emptyIntervalsProduceNoSyntheticCandles() {
        CandleAggregator aggregator = aggregator(Interval.FIVE_MINUTE, clockAt("2026-11-18T09:21:00"));

        aggregator.accept(tick(NSE_KEY, 1L, "2026-11-18T09:15:05", "100"));
        AggregationResult jump = aggregator.accept(tick(NSE_KEY, 2L, "2026-11-18T09:20:30", "101"));

        assertThat(jump.completedCandles()).hasSize(1);
        assertThat(jump.completedCandles().getFirst().timestamp()).isEqualTo(ist("2026-11-18T09:15:00"));
        assertThat(jump.currentBucketStart()).contains(ist("2026-11-18T09:20:00"));
    }

    @Test
    void ticksOutsideTheSessionAreRejectedRatherThanClamped() {
        Clock clock = clockAt("2026-11-18T15:40:00");

        CandleAggregator preOpen = aggregator(Interval.FIVE_MINUTE, clock);
        assertThat(preOpen.accept(tick(NSE_KEY, 1L, "2026-11-18T08:50:00", "100")).status())
                .isEqualTo(AggregationStatus.IGNORED_OUTSIDE_SESSION);
        assertThat(preOpen.currentBucketStart()).isEmpty();

        CandleAggregator postClose = aggregator(Interval.FIVE_MINUTE, clock);
        assertThat(postClose.accept(tick(NSE_KEY, 1L, "2026-11-18T15:35:00", "100")).status())
                .isEqualTo(AggregationStatus.IGNORED_OUTSIDE_SESSION);
        assertThat(postClose.currentBucketStart()).isEmpty();
        assertThat(postClose.flush()).isEmpty();
    }

    @Test
    void resetClearsTheBucketAndTheSequenceWatermark() {
        CandleAggregator aggregator = aggregator(Interval.ONE_MINUTE, clockAt("2026-11-18T09:16:00"));
        aggregator.accept(tick(NSE_KEY, 9L, "2026-11-18T09:15:05", "100"));

        aggregator.reset();

        assertThat(aggregator.currentBucketStart()).isEmpty();
        assertThat(aggregator.lastAcceptedSequence()).isZero();
        assertThat(aggregator.lastTickTimestamp()).isEmpty();
        assertThat(aggregator.flush()).isEmpty();
        assertThat(aggregator.accept(tick(NSE_KEY, 1L, "2026-11-18T09:15:10", "100")).isAccepted()).isTrue();
    }

    @Test
    void feedsWithoutSequenceNumbersFallBackToArrivalOrdering() {
        CandleAggregator aggregator = aggregator(Interval.ONE_MINUTE, clockAt("2026-11-18T09:16:00"));

        aggregator.accept(tick(NSE_KEY, 0L, "2026-11-18T09:15:05", "100"));
        AggregationResult second = aggregator.accept(tick(NSE_KEY, 0L, "2026-11-18T09:15:06", "101"));

        assertThat(second.isAccepted()).isTrue();
        Candle candle = aggregator.flush().orElseThrow();
        assertThat(candle.close()).isEqualByComparingTo("101");
        assertThat(candle.volume()).isEqualTo(20L);
        assertThat(aggregator.lastAcceptedSequence()).isZero();
    }

    @Test
    void nullTickAndInvalidAlignerStepAreRejected() {
        CandleAggregator aggregator = aggregator(Interval.ONE_MINUTE, clockAt("2026-11-18T09:16:00"));

        assertThatThrownBy(() -> aggregator.accept(null))
                .isInstanceOf(com.tradingassistant.pipeline.exception.PipelineValidationException.class);
        assertThatThrownBy(() -> new FixedStepAligner(Instant.EPOCH, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AggregationPolicy(Duration.ofSeconds(-1), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void flushReportsAccumulatedVolumeAndClearsTheBucket() {
        CandleAggregator aggregator = aggregator(Interval.ONE_MINUTE, clockAt("2026-11-18T09:16:00"));
        aggregator.accept(tick(NSE_KEY, 1L, ist("2026-11-18T09:15:05"), new BigDecimal("250"), 3L));

        Candle candle = aggregator.flush().orElseThrow();
        assertThat(candle.volume()).isEqualTo(3L);
        assertThat(aggregator.flush()).isEmpty();
    }

    private static CandleAggregator aggregator(Interval interval, Clock clock) {
        return CandleAggregator.create(
                NSE_KEY, interval, nseCalendar(), AggregationPolicy.defaults(), clock);
    }
}
