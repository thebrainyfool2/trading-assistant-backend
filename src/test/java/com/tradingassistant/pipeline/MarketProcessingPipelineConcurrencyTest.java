package com.tradingassistant.pipeline;

import com.tradingassistant.indicator.config.IndicatorConfig;
import com.tradingassistant.indicator.config.PriceSource;
import com.tradingassistant.indicator.engine.IndicatorEngine;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.pipeline.aggregation.AggregationPolicy;
import com.tradingassistant.pipeline.calendar.MarketCalendarRegistry;
import com.tradingassistant.pipeline.event.CandleCompletedEvent;
import com.tradingassistant.pipeline.event.MarketEventBus;
import com.tradingassistant.pipeline.event.MarketEventSessionListener;
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.tradingassistant.pipeline.PipelineTestFixtures.SESSION_OPEN;
import static com.tradingassistant.pipeline.PipelineTestFixtures.tick;
import static org.assertj.core.api.Assertions.assertThat;

class MarketProcessingPipelineConcurrencyTest {

    @Test
    void independentInstrumentsAreProcessedInParallelWithoutLosingCandles() throws Exception {
        int instruments = 4;
        int minutes = 300;
        Instant end = SESSION_OPEN.plus(Duration.ofMinutes(minutes + 1));
        Clock clock = Clock.fixed(end, ZoneOffset.UTC);

        AtomicInteger candles = new AtomicInteger();
        MarketEventBus bus = new MarketEventBus();
        bus.subscribe(event -> {
            if (event instanceof CandleCompletedEvent) {
                candles.incrementAndGet();
            }
        });
        IndicatorSessionRegistry registry = new IndicatorSessionRegistry(
                IndicatorEngine.standard(),
                SessionRegistryPolicy.unlimited(),
                clock,
                new MarketEventSessionListener(bus, clock));
        MarketProcessingPipeline pipeline = new MarketProcessingPipeline(
                registry, bus, MarketCalendarRegistry.defaults(), AggregationPolicy.defaults(), clock);

        List<InstrumentKey> keys = new ArrayList<>();
        for (int i = 0; i < instruments; i++) {
            InstrumentKey key = new InstrumentKey(Segment.NSE_CASH, String.valueOf(2000 + i));
            keys.add(key);
            pipeline.subscribe(PipelineSubscription.of(
                    key, Interval.ONE_MINUTE,
                    IndicatorConfig.period(5, PriceSource.CLOSE), "SMA", "EMA"));
        }

        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(instruments);
        try {
            for (int index = 0; index < instruments; index++) {
                InstrumentKey key = keys.get(index);
                executor.submit(() -> {
                    try {
                        start.await();
                        for (int minute = 0; minute < minutes; minute++) {
                            pipeline.process(tick(key, minute + 1L,
                                    SESSION_OPEN.plus(Duration.ofMinutes(minute)), "100", 5L));
                        }
                    } catch (Throwable failure) {
                        failures.add(failure);
                    }
                    return null;
                });
            }
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(failures).isEmpty();
        assertThat(candles.get()).isEqualTo(instruments * (minutes - 1));
        assertThat(registry.size()).isEqualTo(instruments);
        assertThat(pipeline.channelCount()).isEqualTo(instruments);
        for (InstrumentKey key : keys) {
            assertThat(registry.keys())
                    .contains(new com.tradingassistant.pipeline.session.IndicatorSessionKey(
                            key, Interval.ONE_MINUTE, IndicatorConfig.period(5, PriceSource.CLOSE)));
        }
    }

    @Test
    void concurrentTicksForOneInstrumentStayConsistentRegardlessOfArrivalOrder() throws Exception {
        Instant end = SESSION_OPEN.plus(Duration.ofMinutes(100));
        Clock clock = Clock.fixed(end, ZoneOffset.UTC);
        MarketEventBus bus = new MarketEventBus();
        List<CandleCompletedEvent> completed = Collections.synchronizedList(new ArrayList<>());
        bus.subscribe(event -> {
            if (event instanceof CandleCompletedEvent candleEvent) {
                completed.add(candleEvent);
            }
        });
        IndicatorSessionRegistry registry = new IndicatorSessionRegistry(
                IndicatorEngine.standard(), SessionRegistryPolicy.unlimited(), clock,
                new MarketEventSessionListener(bus, clock));
        MarketProcessingPipeline pipeline = new MarketProcessingPipeline(
                registry, bus, MarketCalendarRegistry.defaults(), AggregationPolicy.defaults(), clock);

        InstrumentKey key = new InstrumentKey(Segment.NSE_CASH, "3000");
        pipeline.subscribe(PipelineSubscription.of(
                key, Interval.ONE_MINUTE, IndicatorConfig.defaults(), "SMA"));

        int threads = 6;
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            for (int thread = 0; thread < threads; thread++) {
                int offset = thread;
                executor.submit(() -> {
                    try {
                        start.await();
                        pipeline.process(tick(key, offset + 1L,
                                SESSION_OPEN.plus(Duration.ofMinutes(offset)), "100", 1L));
                    } catch (Throwable failure) {
                        failures.add(failure);
                    }
                    return null;
                });
            }
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(failures).isEmpty();
        assertThat(registry.size()).isEqualTo(1);
        assertThat(completed).isNotEmpty();
        assertThat(completed.size()).isLessThanOrEqualTo(threads);
        for (int index = 1; index < completed.size(); index++) {
            assertThat(completed.get(index).candle().timestamp())
                    .isAfter(completed.get(index - 1).candle().timestamp());
        }
        for (CandleCompletedEvent event : completed) {
            assertThat(event.candle().high()).isGreaterThanOrEqualTo(event.candle().low());
            assertThat(event.candle().high()).isGreaterThanOrEqualTo(event.candle().open());
            assertThat(event.candle().high()).isGreaterThanOrEqualTo(event.candle().close());
            assertThat(event.candle().low()).isLessThanOrEqualTo(event.candle().open());
        }
        assertThat(pipeline.flush(key)).hasSizeLessThanOrEqualTo(1);
    }
}
