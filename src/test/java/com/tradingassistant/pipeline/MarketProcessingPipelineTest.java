package com.tradingassistant.pipeline;

import com.tradingassistant.indicator.config.IndicatorConfig;
import com.tradingassistant.indicator.config.PriceSource;
import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.engine.IndicatorEngine;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.pipeline.aggregation.AggregationPolicy;
import com.tradingassistant.pipeline.calendar.MarketCalendarRegistry;
import com.tradingassistant.pipeline.event.CandleCompletedEvent;
import com.tradingassistant.pipeline.event.IndicatorUpdatedEvent;
import com.tradingassistant.pipeline.event.MarketEvent;
import com.tradingassistant.pipeline.event.MarketEventBus;
import com.tradingassistant.pipeline.event.MarketEventSessionListener;
import com.tradingassistant.pipeline.event.MarketEventType;
import com.tradingassistant.pipeline.event.SessionStartedEvent;
import com.tradingassistant.pipeline.exception.PipelineException;
import com.tradingassistant.pipeline.exception.PipelineValidationException;
import com.tradingassistant.pipeline.session.IndicatorSessionRegistry;
import com.tradingassistant.pipeline.session.SessionRegistryPolicy;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.tradingassistant.pipeline.PipelineTestFixtures.NSE_KEY;
import static com.tradingassistant.pipeline.PipelineTestFixtures.SECOND_KEY;
import static com.tradingassistant.pipeline.PipelineTestFixtures.SESSION_OPEN;
import static com.tradingassistant.pipeline.PipelineTestFixtures.tick;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketProcessingPipelineTest {

    private static final Instant LAST_TICK = SESSION_OPEN.plus(Duration.ofMinutes(30));

    @Test
    void publishesTheDocumentedEventSequence() {
        Harness harness = harness(LAST_TICK);
        PipelineSubscription subscription = subscription(Interval.ONE_MINUTE, "SMA");
        harness.pipeline.subscribe(subscription);

        harness.pipeline.process(tick(NSE_KEY, 1L, "2026-11-18T09:15:05", "100"));
        harness.pipeline.process(tick(NSE_KEY, 2L, "2026-11-18T09:16:05", "102"));

        assertThat(harness.types()).containsExactly(
                MarketEventType.TICK_RECEIVED,
                MarketEventType.TICK_RECEIVED,
                MarketEventType.CANDLE_COMPLETED,
                MarketEventType.SESSION_STARTED,
                MarketEventType.INDICATOR_UPDATED);

        SessionStartedEvent started = harness.first(SessionStartedEvent.class);
        assertThat(started.session()).isEqualTo(subscription.sessionKey());
        assertThat(started.indicatorTypes()).containsExactly("SMA");

        CandleCompletedEvent candleEvent = harness.first(CandleCompletedEvent.class);
        assertThat(candleEvent.candle().timestamp()).isEqualTo(SESSION_OPEN);
        assertThat(candleEvent.candle().close()).isEqualByComparingTo("100");
    }

    @Test
    void computesIndicatorValuesFromAggregatedCandles() {
        Harness harness = harness(LAST_TICK);
        PipelineSubscription subscription = subscription(
                Interval.ONE_MINUTE, IndicatorConfig.period(2, PriceSource.CLOSE), "SMA");
        harness.pipeline.subscribe(subscription);

        harness.pipeline.process(tick(NSE_KEY, 1L, "2026-11-18T09:15:05", "100"));
        harness.pipeline.process(tick(NSE_KEY, 2L, "2026-11-18T09:16:05", "102"));

        List<IndicatorUpdatedEvent> updates = harness.all(IndicatorUpdatedEvent.class);
        assertThat(updates).hasSize(1);
        assertThat(updates.getFirst().results().get("SMA").warmup()).isTrue();

        harness.pipeline.process(tick(NSE_KEY, 3L, "2026-11-18T09:17:05", "104"));

        IndicatorResult sma = harness.all(IndicatorUpdatedEvent.class).getLast().results().get("SMA");
        assertThat(sma.warmup()).isFalse();
        assertThat(sma.requiredValue("value")).isEqualByComparingTo("101");
        assertThat(harness.registry.size()).isEqualTo(1);
        assertThat(harness.registry.require(subscription.sessionKey()).candleCount()).isEqualTo(2);
    }

    @Test
    void multipleIntervalsForOneInstrumentAreAggregatedIndependently() {
        Harness harness = harness(LAST_TICK);
        harness.pipeline.subscribe(subscription(Interval.ONE_MINUTE, "SMA"));
        harness.pipeline.subscribe(subscription(Interval.FIVE_MINUTE, "SMA"));

        harness.pipeline.process(tick(NSE_KEY, 1L, "2026-11-18T09:15:05", "100"));
        harness.pipeline.process(tick(NSE_KEY, 2L, "2026-11-18T09:16:05", "102"));
        harness.pipeline.process(tick(NSE_KEY, 3L, "2026-11-18T09:20:05", "104"));
        harness.pipeline.process(tick(NSE_KEY, 4L, "2026-11-18T09:21:05", "106"));

        assertThat(harness.candles(Interval.ONE_MINUTE)).hasSize(3);
        assertThat(harness.candles(Interval.FIVE_MINUTE)).hasSize(1);
        assertThat(harness.registry.size()).isEqualTo(2);
        assertThat(harness.pipeline.channelCount()).isEqualTo(2);
    }

    @Test
    void ticksForAnUnsubscribedInstrumentPublishOnlyTheTickEvent() {
        Harness harness = harness(LAST_TICK);

        PipelineOutcome outcome = harness.pipeline.process(
                tick(NSE_KEY, 1L, "2026-11-18T09:15:05", "100"));

        assertThat(outcome).isEqualTo(new PipelineOutcome(0, 0, 0, 1));
        assertThat(harness.types()).containsExactly(MarketEventType.TICK_RECEIVED);
        assertThat(harness.registry.size()).isZero();
    }

    @Test
    void duplicateSubscriptionsAreIdempotent() {
        Harness harness = harness(LAST_TICK);
        PipelineSubscription subscription = subscription(Interval.ONE_MINUTE, "SMA");

        harness.pipeline.subscribe(subscription);
        harness.pipeline.subscribe(subscription);

        assertThat(harness.pipeline.channelCount()).isEqualTo(1);
        assertThat(harness.pipeline.subscribedInstruments()).containsExactly(NSE_KEY);
        assertThat(harness.pipeline.aggregator(NSE_KEY, Interval.ONE_MINUTE)).isPresent();
        assertThat(harness.pipeline.aggregator(null, Interval.ONE_MINUTE)).isEmpty();
    }

    @Test
    void invalidSubscriptionsAreRejectedBeforeAnyTick() {
        Harness harness = harness(LAST_TICK);

        assertThatThrownBy(() -> harness.pipeline.subscribe(
                PipelineSubscription.of(NSE_KEY, Interval.ONE_MINUTE, IndicatorConfig.defaults())))
                .isInstanceOf(PipelineValidationException.class);
        assertThatThrownBy(() -> harness.pipeline.subscribe(
                PipelineSubscription.of(NSE_KEY, Interval.ONE_MINUTE, IndicatorConfig.defaults(), "NOPE")))
                .isInstanceOf(PipelineValidationException.class);
        assertThat(harness.pipeline.channelCount()).isZero();
    }

    @Test
    void flushPublishesTheFormingCandleAndAdvancesIndicators() {
        Harness harness = harness(LAST_TICK);
        PipelineSubscription subscription = subscription(
                Interval.FIVE_MINUTE, IndicatorConfig.period(2, PriceSource.CLOSE), "SMA");
        harness.pipeline.subscribe(subscription);
        harness.pipeline.process(tick(NSE_KEY, 1L, "2026-11-18T09:15:05", "100"));

        List<Candle> flushed = harness.pipeline.flush(NSE_KEY);

        assertThat(flushed).hasSize(1);
        assertThat(flushed.getFirst().timestamp()).isEqualTo(SESSION_OPEN);
        assertThat(harness.registry.require(subscription.sessionKey()).candleCount()).isEqualTo(1);
        assertThat(harness.pipeline.flush(NSE_KEY)).isEmpty();
    }

    @Test
    void unsubscribeDiscardsTheFormingCandleAndStopsProcessing() {
        Harness harness = harness(LAST_TICK);
        harness.pipeline.subscribe(subscription(Interval.ONE_MINUTE, "SMA"));
        harness.pipeline.process(tick(NSE_KEY, 1L, "2026-11-18T09:15:05", "100"));

        assertThat(harness.pipeline.unsubscribe(NSE_KEY)).isTrue();

        assertThat(harness.pipeline.flush(NSE_KEY)).isEmpty();
        assertThat(harness.pipeline.unsubscribe(NSE_KEY)).isFalse();
        harness.events.clear();
        PipelineOutcome outcome = harness.pipeline.process(
                tick(NSE_KEY, 2L, "2026-11-18T09:16:05", "102"));
        assertThat(outcome.eventsPublished()).isEqualTo(1);
        assertThat(harness.types()).containsExactly(MarketEventType.TICK_RECEIVED);
    }

    @Test
    void closingThePipelineFlushesAndRejectsFurtherTicks() {
        Harness harness = harness(LAST_TICK);
        harness.pipeline.subscribe(subscription(Interval.ONE_MINUTE, "SMA"));
        harness.pipeline.process(tick(NSE_KEY, 1L, "2026-11-18T09:15:05", "100"));

        harness.pipeline.close();

        assertThat(harness.candles(Interval.ONE_MINUTE)).hasSize(1);
        assertThatThrownBy(() -> harness.pipeline.process(tick(NSE_KEY, 2L, "2026-11-18T09:16:05", "100")))
                .isInstanceOf(PipelineException.class);
        assertThatThrownBy(() -> harness.pipeline.subscribe(subscription(Interval.FIVE_MINUTE, "SMA")))
                .isInstanceOf(PipelineException.class);
    }

    @Test
    void instrumentsDoNotShareSessionsOrCandles() {
        Harness harness = harness(LAST_TICK);
        harness.pipeline.subscribe(subscription(NSE_KEY, Interval.ONE_MINUTE, "SMA"));
        harness.pipeline.subscribe(subscription(SECOND_KEY, Interval.ONE_MINUTE, "SMA"));

        harness.pipeline.process(tick(NSE_KEY, 1L, "2026-11-18T09:15:05", "100"));
        harness.pipeline.process(tick(SECOND_KEY, 1L, "2026-11-18T09:15:06", "500"));
        harness.pipeline.process(tick(NSE_KEY, 2L, "2026-11-18T09:16:05", "102"));
        harness.pipeline.process(tick(SECOND_KEY, 2L, "2026-11-18T09:16:06", "502"));

        List<Candle> candles = harness.candles(Interval.ONE_MINUTE);
        assertThat(harness.registry.size()).isEqualTo(2);
        assertThat(candles).hasSize(2);
        Candle nseCandle = candles.stream()
                .filter(candle -> candle.instrument().equals(NSE_KEY))
                .findFirst()
                .orElseThrow();
        Candle secondCandle = candles.stream()
                .filter(candle -> candle.instrument().equals(SECOND_KEY))
                .findFirst()
                .orElseThrow();
        assertThat(nseCandle.close()).isEqualByComparingTo("100");
        assertThat(nseCandle.open()).isEqualByComparingTo("100");
        assertThat(secondCandle.close()).isEqualByComparingTo("500");
    }

    @Test
    void aLargeReplayDatasetIsProcessedDeterministically() {
        int instruments = 10;
        int minutes = 300;
        Instant end = SESSION_OPEN.plus(Duration.ofMinutes(minutes));
        Harness harness = harness(end);
        List<InstrumentKey> keys = new ArrayList<>();
        for (int i = 0; i < instruments; i++) {
            InstrumentKey key = new InstrumentKey(
                    com.tradingassistant.market.domain.Segment.NSE_CASH, String.valueOf(1000 + i));
            keys.add(key);
            harness.pipeline.subscribe(subscription(key, Interval.ONE_MINUTE,
                    IndicatorConfig.period(5, PriceSource.CLOSE), "SMA", "EMA"));
        }

        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            for (int minute = 0; minute < minutes; minute++) {
                for (int index = 0; index < instruments; index++) {
                    InstrumentKey key = keys.get(index);
                    harness.pipeline.process(tick(key, minute + 1L,
                            SESSION_OPEN.plus(Duration.ofMinutes(minute)), "100", 5L));
                }
            }
        });

        assertThat(harness.registry.size()).isEqualTo(instruments);
        assertThat(harness.candles(Interval.ONE_MINUTE)).hasSize(instruments * (minutes - 1));
        assertThat(harness.pipeline.subscribedInstruments()).hasSize(instruments);
    }

    @Test
    void nullTicksAreRejected() {
        Harness harness = harness(LAST_TICK);
        assertThatThrownBy(() -> harness.pipeline.process(null))
                .isInstanceOf(PipelineValidationException.class);
    }

    private static PipelineSubscription subscription(Interval interval, String... types) {
        return subscription(NSE_KEY, interval, IndicatorConfig.defaults(), types);
    }

    private static PipelineSubscription subscription(
            Interval interval, IndicatorConfig config, String... types) {
        return subscription(NSE_KEY, interval, config, types);
    }

    private static PipelineSubscription subscription(
            InstrumentKey instrument, Interval interval, String... types) {
        return subscription(instrument, interval, IndicatorConfig.defaults(), types);
    }

    private static PipelineSubscription subscription(
            InstrumentKey instrument, Interval interval, IndicatorConfig config, String... types) {
        return PipelineSubscription.of(instrument, interval, config, types);
    }

    private static Harness harness(Instant now) {
        return new Harness(now);
    }

    /**
     * Pipeline wired to a recording event bus, mirroring the production wiring.
     */
    private static final class Harness {

        private final List<MarketEvent> events = Collections.synchronizedList(new ArrayList<>());
        private final IndicatorSessionRegistry registry;
        private final MarketProcessingPipeline pipeline;
        private final MarketEventBus bus = new MarketEventBus();

        Harness(Instant now) {
            Clock clock = Clock.fixed(now, ZoneOffset.UTC);
            bus.subscribe(events::add);
            registry = new IndicatorSessionRegistry(
                    IndicatorEngine.standard(),
                    SessionRegistryPolicy.unlimited(),
                    clock,
                    new MarketEventSessionListener(bus, clock));
            pipeline = new MarketProcessingPipeline(
                    registry, bus, MarketCalendarRegistry.defaults(),
                    AggregationPolicy.defaults(), clock);
        }

        List<MarketEventType> types() {
            return events.stream().map(MarketEvent::type).toList();
        }

        <T extends MarketEvent> List<T> all(Class<T> type) {
            return events.stream().filter(type::isInstance).map(type::cast).toList();
        }

        <T extends MarketEvent> T first(Class<T> type) {
            return all(type).getFirst();
        }

        List<Candle> candles(Interval interval) {
            return all(CandleCompletedEvent.class).stream()
                    .map(CandleCompletedEvent::candle)
                    .filter(candle -> candle.interval() == interval)
                    .toList();
        }
    }
}
