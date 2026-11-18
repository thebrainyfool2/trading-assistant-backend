package com.tradingassistant.pipeline;

import com.tradingassistant.indicator.config.IndicatorConfig;
import com.tradingassistant.indicator.config.PriceSource;
import com.tradingassistant.indicator.engine.IndicatorEngine;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.pipeline.aggregation.AggregationPolicy;
import com.tradingassistant.pipeline.calendar.MarketCalendarRegistry;
import com.tradingassistant.pipeline.event.MarketEvent;
import com.tradingassistant.pipeline.event.MarketEventBus;
import com.tradingassistant.pipeline.event.MarketEventPublisher;
import com.tradingassistant.pipeline.event.MarketEventSessionListener;
import com.tradingassistant.pipeline.event.SessionExpiredEvent;
import com.tradingassistant.pipeline.session.IndicatorSessionKey;
import com.tradingassistant.pipeline.session.IndicatorSessionRegistry;
import com.tradingassistant.pipeline.session.IndicatorSessionSnapshot;
import com.tradingassistant.pipeline.session.SessionRegistryPolicy;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.tradingassistant.pipeline.PipelineTestFixtures.NSE_KEY;
import static com.tradingassistant.pipeline.PipelineTestFixtures.SESSION_OPEN;
import static com.tradingassistant.pipeline.PipelineTestFixtures.tick;
import static org.assertj.core.api.Assertions.assertThat;

class MarketSessionLifecycleManagerTest {

    private static final Instant NOW = SESSION_OPEN.plus(Duration.ofMinutes(10));

    @Test
    void startAndShutdownFollowTheDocumentedOrder() {
        Harness harness = harness(NOW, SessionRegistryPolicy.unlimited());
        harness.pipeline.subscribe(PipelineSubscription.of(
                NSE_KEY, Interval.ONE_MINUTE, IndicatorConfig.period(2, PriceSource.CLOSE), "SMA"));
        harness.pipeline.process(tick(NSE_KEY, 1L, "2026-11-18T09:15:05", "100"));

        harness.lifecycle.start();
        assertThat(harness.lifecycle.isStarted()).isTrue();
        assertThat(harness.lifecycle.startedAt()).contains(NOW);

        List<Candle> drained = harness.lifecycle.drain();
        assertThat(drained).hasSize(1);
        assertThat(drained.getFirst().timestamp()).isEqualTo(SESSION_OPEN);

        List<IndicatorSessionKey> expired = harness.lifecycle.shutdown();

        assertThat(expired).hasSize(1);
        assertThat(harness.registry.size()).isZero();
        assertThat(harness.lifecycle.isStarted()).isFalse();
        SessionExpiredEvent event = harness.events.stream()
                .filter(SessionExpiredEvent.class::isInstance)
                .map(SessionExpiredEvent.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(event.reason()).isEqualTo(MarketSessionLifecycleManager.REASON_SHUTDOWN);
    }

    @Test
    void sweepExpiresOnlySessionsBeyondTheirIdleTimeout() {
        PipelineTestFixtures.MutableClock clock = new PipelineTestFixtures.MutableClock(NOW);
        Harness harness = harness(clock, new SessionRegistryPolicy(Duration.ofMinutes(5), 10));
        IndicatorSessionKey key = new IndicatorSessionKey(
                NSE_KEY, Interval.ONE_MINUTE, IndicatorConfig.defaults());
        harness.registry.getOrCreate(key, Set.of("SMA"));

        assertThat(harness.lifecycle.sweep()).isZero();

        clock.advance(Duration.ofMinutes(6));
        assertThat(harness.lifecycle.sweep()).isEqualTo(1);
        assertThat(harness.lifecycle.sweep()).isZero();
        assertThat(harness.registry.size()).isZero();
    }

    @Test
    void closeSessionUsesTheClosedReasonAndKeepsThePipelineRunning() {
        Harness harness = harness(NOW, SessionRegistryPolicy.unlimited());
        IndicatorSessionKey key = new IndicatorSessionKey(
                NSE_KEY, Interval.ONE_MINUTE, IndicatorConfig.defaults());
        harness.registry.getOrCreate(key, Set.of("SMA"));

        assertThat(harness.lifecycle.closeSession(key)).isTrue();
        assertThat(harness.lifecycle.closeSession(key)).isFalse();
        assertThat(harness.registry.size()).isZero();
        assertThat(harness.eventReasons()).containsExactly(MarketSessionLifecycleManager.REASON_CLOSED);
    }

    @Test
    void snapshotsAndRestoreRebuildSessionBindings() {
        Harness harness = harness(NOW, SessionRegistryPolicy.unlimited());
        IndicatorSessionKey key = new IndicatorSessionKey(
                NSE_KEY, Interval.ONE_MINUTE, IndicatorConfig.defaults());
        harness.registry.getOrCreate(key, Set.of("SMA"));

        List<IndicatorSessionSnapshot> snapshots = harness.lifecycle.snapshots();
        harness.lifecycle.shutdown();

        assertThat(harness.lifecycle.restore(snapshots)).isEqualTo(1);
        assertThat(harness.registry.size()).isEqualTo(1);
        assertThat(harness.lifecycle.restore(List.of())).isZero();
    }

    @Test
    void closeIsIdempotent() {
        Harness harness = harness(NOW, SessionRegistryPolicy.unlimited());
        harness.lifecycle.start();

        harness.lifecycle.close();
        harness.lifecycle.close();

        assertThat(harness.lifecycle.isStarted()).isFalse();
        assertThat(harness.registry.size()).isZero();
    }

    private static Harness harness(Instant now, SessionRegistryPolicy policy) {
        return harness(PipelineTestFixtures.clockAt(now), policy);
    }

    private static Harness harness(Clock clock, SessionRegistryPolicy policy) {
        return new Harness(clock, policy);
    }

    private static final class Harness {

        private final List<MarketEvent> events = Collections.synchronizedList(new ArrayList<>());
        private final MarketEventBus bus = new MarketEventBus();
        private final IndicatorSessionRegistry registry;
        private final MarketProcessingPipeline pipeline;
        private final MarketSessionLifecycleManager lifecycle;

        Harness(Clock clock, SessionRegistryPolicy policy) {
            MarketEventPublisher publisher = bus;
            bus.subscribe(events::add);
            registry = new IndicatorSessionRegistry(
                    IndicatorEngine.standard(), policy, clock,
                    new MarketEventSessionListener(publisher, clock));
            pipeline = new MarketProcessingPipeline(
                    registry, publisher, MarketCalendarRegistry.defaults(),
                    AggregationPolicy.defaults(), clock);
            lifecycle = new MarketSessionLifecycleManager(pipeline, registry, clock);
        }

        List<String> eventReasons() {
            return events.stream()
                    .filter(SessionExpiredEvent.class::isInstance)
                    .map(SessionExpiredEvent.class::cast)
                    .map(SessionExpiredEvent::reason)
                    .toList();
        }
    }
}
