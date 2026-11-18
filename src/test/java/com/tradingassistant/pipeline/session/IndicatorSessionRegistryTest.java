package com.tradingassistant.pipeline.session;

import com.tradingassistant.indicator.config.IndicatorConfig;
import com.tradingassistant.indicator.config.PriceSource;
import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.engine.IndicatorEngine;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.pipeline.PipelineTestFixtures;
import com.tradingassistant.pipeline.exception.PipelineValidationException;
import com.tradingassistant.pipeline.exception.SessionExpiredException;
import com.tradingassistant.pipeline.exception.SessionLimitExceededException;
import com.tradingassistant.pipeline.exception.SessionNotFoundException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.tradingassistant.pipeline.PipelineTestFixtures.NSE_KEY;
import static com.tradingassistant.pipeline.PipelineTestFixtures.SECOND_KEY;
import static com.tradingassistant.pipeline.PipelineTestFixtures.SESSION_OPEN;
import static com.tradingassistant.pipeline.PipelineTestFixtures.candle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IndicatorSessionRegistryTest {

    private static final Instant NOW = SESSION_OPEN.plus(Duration.ofMinutes(10));

    @Test
    void createsOneSessionPerKeyAndReusesIt() {
        IndicatorSessionRegistry registry = registry(clockAt(NOW), SessionRegistryPolicy.unlimited());
        IndicatorSessionKey key = key(Interval.ONE_MINUTE, config());

        IndicatorSession first = registry.getOrCreate(key, Set.of("SMA"));
        IndicatorSession second = registry.getOrCreate(key, Set.of("SMA"));

        assertThat(first).isSameAs(second);
        assertThat(first.indicatorTypes()).containsExactly("SMA");
        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.keys()).containsExactly(key);
    }

    @Test
    void intervalConfigurationAndInstrumentArePartOfIdentity() {
        IndicatorSessionRegistry registry = registry(clockAt(NOW), SessionRegistryPolicy.unlimited());

        registry.getOrCreate(key(Interval.ONE_MINUTE, config()), Set.of("SMA"));
        registry.getOrCreate(key(Interval.FIVE_MINUTE, config()), Set.of("SMA"));
        registry.getOrCreate(new IndicatorSessionKey(
                NSE_KEY, Interval.ONE_MINUTE, IndicatorConfig.period(9, PriceSource.CLOSE)), Set.of("SMA"));
        registry.getOrCreate(new IndicatorSessionKey(
                SECOND_KEY, Interval.ONE_MINUTE, config()), Set.of("SMA"));

        assertThat(registry.size()).isEqualTo(4);
    }

    @Test
    void equivalentConfigurationsWithDifferentScalesShareASession() {
        IndicatorSessionRegistry registry = registry(clockAt(NOW), SessionRegistryPolicy.unlimited());
        IndicatorConfig coarse = IndicatorConfig.bollinger(20, new BigDecimal("2"), PriceSource.CLOSE);
        IndicatorConfig fine = IndicatorConfig.bollinger(20, new BigDecimal("2.00"), PriceSource.CLOSE);

        IndicatorSession first = registry.getOrCreate(
                new IndicatorSessionKey(NSE_KEY, Interval.FIVE_MINUTE, coarse), Set.of("BOLLINGER"));
        IndicatorSession second = registry.getOrCreate(
                new IndicatorSessionKey(NSE_KEY, Interval.FIVE_MINUTE, fine), Set.of("BOLLINGER"));

        assertThat(first).isSameAs(second);
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void rejectsEmptyAndUnknownIndicatorTypes() {
        IndicatorSessionRegistry registry = registry(clockAt(NOW), SessionRegistryPolicy.unlimited());

        assertThatThrownBy(() -> registry.getOrCreate(key(Interval.ONE_MINUTE, config()), Set.of()))
                .isInstanceOf(PipelineValidationException.class);
        assertThatThrownBy(() -> registry.getOrCreate(key(Interval.ONE_MINUTE, config()), Set.of("NOPE")))
                .isInstanceOf(PipelineValidationException.class);
        assertThatThrownBy(() -> registry.getOrCreate(key(Interval.ONE_MINUTE, config()), Set.of(" ")))
                .isInstanceOf(PipelineValidationException.class);
        assertThat(registry.size()).isZero();
    }

    @Test
    void updatesAdvanceEveryIndicatorAndKeepWarmupRows() {
        IndicatorSessionRegistry registry = registry(clockAt(NOW), SessionRegistryPolicy.unlimited());
        IndicatorSessionKey key = key(Interval.ONE_MINUTE, IndicatorConfig.period(3, PriceSource.CLOSE));
        IndicatorSession session = registry.getOrCreate(key, Set.of("SMA", "EMA"));

        IndicatorSessionUpdate first = session.update(candle(0, "100"), NOW);
        session.update(candle(1, "102"), NOW);
        IndicatorSessionUpdate third = session.update(candle(2, "104"), NOW);

        assertThat(first.results().keySet()).containsExactlyInAnyOrder("SMA", "EMA");
        assertThat(first.results().get("SMA").warmup()).isTrue();
        IndicatorResult sma = third.results().get("SMA");
        assertThat(sma.warmup()).isFalse();
        assertThat(sma.requiredValue("value")).isEqualByComparingTo("102");
        assertThat(session.candleCount()).isEqualTo(3);
        assertThat(session.lastCandleTimestamp()).contains(candle(2, "104").timestamp());
        assertThat(session.lastUpdatedAt()).contains(NOW);
        assertThat(session.result("sma")).contains(sma);
        assertThat(session.result(null)).isEmpty();
    }

    @Test
    void resetClearsRecursiveStateWithoutLosingTheSession() {
        IndicatorSessionRegistry registry = registry(clockAt(NOW), SessionRegistryPolicy.unlimited());
        IndicatorSessionKey key = key(Interval.ONE_MINUTE, IndicatorConfig.period(2, PriceSource.CLOSE));
        IndicatorSession session = registry.getOrCreate(key, Set.of("SMA"));
        session.update(candle(0, "100"), NOW);

        IndicatorSession reset = registry.reset(key);

        assertThat(reset).isSameAs(session);
        assertThat(reset.candleCount()).isZero();
        assertThat(reset.results()).isEmpty();
        assertThat(reset.lastCandleTimestamp()).isEmpty();
        assertThat(registry.find(key)).isPresent();
    }

    @Test
    void idleSessionsExpireAndCannotBeReused() {
        PipelineTestFixtures.MutableClock clock = new PipelineTestFixtures.MutableClock(NOW);
        IndicatorSessionRegistry registry = registry(
                clock, new SessionRegistryPolicy(Duration.ofMinutes(5), 100));
        IndicatorSessionKey key = key(Interval.ONE_MINUTE, config());
        IndicatorSession session = registry.getOrCreate(key, Set.of("SMA"));

        clock.advance(Duration.ofMinutes(6));
        List<IndicatorSessionKey> expired = registry.expireIdle();

        assertThat(expired).containsExactly(key);
        assertThat(session.state()).isEqualTo(IndicatorSessionState.EXPIRED);
        assertThat(session.expiryReason()).contains("idle");
        assertThat(registry.find(key)).isEmpty();
        assertThat(registry.size()).isZero();
        assertThatThrownBy(() -> session.update(candle(0, "100"), clock.instant()))
                .isInstanceOf(SessionExpiredException.class);
    }

    @Test
    void aRecentlyUsedSessionSurvivesTheSweepAndFindDoesNotExtendIt() {
        PipelineTestFixtures.MutableClock clock = new PipelineTestFixtures.MutableClock(NOW);
        IndicatorSessionRegistry registry = registry(
                clock, new SessionRegistryPolicy(Duration.ofMinutes(5), 100));
        IndicatorSessionKey key = key(Interval.ONE_MINUTE, config());
        registry.getOrCreate(key, Set.of("SMA"));

        clock.advance(Duration.ofMinutes(6));
        registry.find(key).orElseThrow();
        assertThat(registry.expireIdle()).hasSize(1);

        IndicatorSessionKey secondKey = new IndicatorSessionKey(
                SECOND_KEY, Interval.FIVE_MINUTE, config());
        registry.getOrCreate(secondKey, Set.of("SMA"));
        clock.advance(Duration.ofMinutes(4));
        registry.getOrCreate(secondKey, Set.of("SMA"));
        clock.advance(Duration.ofMinutes(4));
        assertThat(registry.expireIdle()).isEmpty();
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void idleExpiryCanBeDisabled() {
        PipelineTestFixtures.MutableClock clock = new PipelineTestFixtures.MutableClock(NOW);
        IndicatorSessionRegistry registry = registry(
                clock, new SessionRegistryPolicy(Duration.ZERO, 10));
        registry.getOrCreate(key(Interval.ONE_MINUTE, config()), Set.of("SMA"));

        clock.advance(Duration.ofDays(1));

        assertThat(registry.expireIdle()).isEmpty();
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void theSessionLimitIsEnforcedWithoutSilentEviction() {
        IndicatorSessionRegistry registry = registry(
                clockAt(NOW), new SessionRegistryPolicy(Duration.ofMinutes(5), 2));
        registry.getOrCreate(key(Interval.ONE_MINUTE, config()), Set.of("SMA"));
        registry.getOrCreate(key(Interval.FIVE_MINUTE, config()), Set.of("SMA"));

        assertThatThrownBy(() -> registry.getOrCreate(key(Interval.FIFTEEN_MINUTE, config()), Set.of("SMA")))
                .isInstanceOf(SessionLimitExceededException.class);
        assertThat(registry.size()).isEqualTo(2);
    }

    @Test
    void listenerReceivesStartAndExpiryNotifications() {
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        SessionLifecycleListener listener = new SessionLifecycleListener() {
            @Override
            public void onSessionStarted(IndicatorSession session) {
                events.add("started:" + session.indicatorCount());
            }

            @Override
            public void onSessionExpired(IndicatorSession session, String reason) {
                events.add("expired:" + reason);
            }
        };
        IndicatorSessionRegistry registry = new IndicatorSessionRegistry(
                IndicatorEngine.standard(),
                new SessionRegistryPolicy(Duration.ofMinutes(5), 100),
                clockAt(NOW),
                listener);
        IndicatorSessionKey key = key(Interval.ONE_MINUTE, config());

        registry.getOrCreate(key, Set.of("SMA"));
        registry.remove(key, "closed");

        assertThat(events).containsExactly("started:1", "expired:closed");
        assertThat(registry.remove(key, "closed")).isFalse();
    }

    @Test
    void snapshotsDescribeSessionsAndRestoreRecreatesTheBinding() {
        IndicatorSessionRegistry registry = registry(clockAt(NOW), SessionRegistryPolicy.unlimited());
        IndicatorSession session = registry.getOrCreate(
                key(Interval.ONE_MINUTE, IndicatorConfig.period(2, PriceSource.CLOSE)), Set.of("SMA"));
        session.update(candle(0, "100"), NOW);

        IndicatorSessionSnapshot snapshot = registry.snapshots().getFirst();
        assertThat(snapshot.candleCount()).isEqualTo(1);
        assertThat(snapshot.indicatorTypes()).containsExactly("SMA");
        assertThat(snapshot.state()).isEqualTo(IndicatorSessionState.ACTIVE);

        registry.expireAll("shutdown");
        assertThat(registry.size()).isZero();

        IndicatorSession restored = registry.restore(snapshot);
        assertThat(restored.isActive()).isTrue();
        assertThat(restored.candleCount()).isZero();
        assertThat(restored.indicatorTypes()).containsExactly("SMA");
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void candlesFromAnotherInstrumentOrIntervalAreRejected() {
        IndicatorSessionRegistry registry = registry(clockAt(NOW), SessionRegistryPolicy.unlimited());
        IndicatorSession session = registry.getOrCreate(
                key(Interval.ONE_MINUTE, config()), Set.of("SMA"));

        assertThatThrownBy(() -> session.update(
                PipelineTestFixtures.candle(SECOND_KEY, Interval.ONE_MINUTE, SESSION_OPEN, 0, "100"), NOW))
                .isInstanceOf(PipelineValidationException.class);
        assertThatThrownBy(() -> session.update(
                PipelineTestFixtures.candle(NSE_KEY, Interval.FIVE_MINUTE, SESSION_OPEN, 0, "100"), NOW))
                .isInstanceOf(PipelineValidationException.class);
        assertThat(session.candleCount()).isZero();
    }

    @Test
    void requireFailsForAnUnknownKey() {
        IndicatorSessionRegistry registry = registry(clockAt(NOW), SessionRegistryPolicy.unlimited());

        assertThatThrownBy(() -> registry.require(key(Interval.ONE_MINUTE, config())))
                .isInstanceOf(SessionNotFoundException.class);
        assertThatThrownBy(() -> registry.reset(key(Interval.ONE_MINUTE, config())))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void concurrentCreatesForOneKeyObserveTheSameSession() throws Exception {
        IndicatorSessionRegistry registry = registry(clockAt(NOW), SessionRegistryPolicy.unlimited());
        IndicatorSessionKey key = key(Interval.ONE_MINUTE, config());
        int threads = 8;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<IndicatorSession> observed = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    observed.add(registry.getOrCreate(key, Set.of("SMA", "EMA")));
                    return null;
                });
            }
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(observed).hasSize(threads);
        assertThat(observed.stream().distinct()).hasSize(1);
        assertThat(registry.size()).isEqualTo(1);
    }

    private static IndicatorSessionRegistry registry(Instant now, SessionRegistryPolicy policy) {
        return registry(PipelineTestFixtures.clockAt(now), policy);
    }

    private static IndicatorSessionRegistry registry(
            java.time.Clock clock, SessionRegistryPolicy policy) {
        return new IndicatorSessionRegistry(
                IndicatorEngine.standard(), policy, clock, SessionLifecycleListener.noop());
    }

    private static IndicatorSessionKey key(Interval interval, IndicatorConfig config) {
        return new IndicatorSessionKey(NSE_KEY, interval, config);
    }

    private static IndicatorConfig config() {
        return IndicatorConfig.period(3, PriceSource.CLOSE);
    }

    private static java.time.Clock clockAt(Instant instant) {
        return PipelineTestFixtures.clockAt(instant);
    }

}
