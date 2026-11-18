package com.tradingassistant.pipeline.event;

import com.tradingassistant.indicator.config.IndicatorConfig;
import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.engine.IndicatorEngine;
import com.tradingassistant.indicator.domain.IndicatorStatus;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.pipeline.PipelineTestFixtures;
import com.tradingassistant.pipeline.exception.EventDeliveryException;
import com.tradingassistant.pipeline.exception.PipelineException;
import com.tradingassistant.pipeline.exception.PipelineValidationException;
import com.tradingassistant.pipeline.session.IndicatorSessionKey;
import com.tradingassistant.pipeline.session.IndicatorSessionRegistry;
import com.tradingassistant.pipeline.session.SessionRegistryPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketEventBusTest {

    private static final Instant NOW = Instant.parse("2026-11-18T04:00:00Z");

    @Test
    void deliversEveryEventToSubscribersInSubscriptionOrder() {
        MarketEventBus bus = new MarketEventBus();
        List<MarketEventType> first = new ArrayList<>();
        List<MarketEventType> second = new ArrayList<>();

        bus.subscribe(event -> first.add(event.type()));
        bus.subscribe(event -> second.add(event.type()));
        bus.publish(new TickReceivedEvent(PipelineTestFixtures.tick(
                PipelineTestFixtures.NSE_KEY, 1L, NOW, "100", 10L), NOW));

        assertThat(first).containsExactly(MarketEventType.TICK_RECEIVED);
        assertThat(second).containsExactly(MarketEventType.TICK_RECEIVED);
        assertThat(bus.subscriberCount()).isEqualTo(2);
    }

    @Test
    void cancellingASubscriptionStopsDelivery() {
        MarketEventBus bus = new MarketEventBus();
        List<MarketEvent> received = new ArrayList<>();
        MarketEventBus.Subscription subscription = bus.subscribe(received::add);

        subscription.cancel();
        subscription.cancel();
        bus.publish(new SessionExpiredEvent(sessionKey(), "idle", NOW));

        assertThat(received).isEmpty();
        assertThat(subscription.isActive()).isFalse();
        assertThat(bus.subscriberCount()).isZero();
    }

    @Test
    void aFailingSubscriberDoesNotPreventOthersAndIsReported() {
        MarketEventBus bus = new MarketEventBus();
        List<MarketEvent> received = new ArrayList<>();
        bus.subscribe(event -> {
            throw new IllegalStateException("consumer failed");
        });
        bus.subscribe(received::add);

        assertThatThrownBy(() -> bus.publish(new SessionStartedEvent(sessionKey(), Set.of("SMA"), NOW)))
                .isInstanceOf(EventDeliveryException.class)
                .hasMessageContaining("1 listener failure");
        assertThat(received).hasSize(1);
    }

    @Test
    void multipleListenerFailuresAreAllRetained() {
        MarketEventBus bus = new MarketEventBus();
        bus.subscribe(event -> {
            throw new IllegalStateException("first");
        });
        bus.subscribe(event -> {
            throw new IllegalArgumentException("second");
        });

        assertThatThrownBy(() -> bus.publish(new SessionStartedEvent(sessionKey(), Set.of("SMA"), NOW)))
                .isInstanceOf(EventDeliveryException.class)
                .satisfies(failure -> {
                    assertThat(failure.getCause()).hasMessageContaining("first");
                    assertThat(failure.getSuppressed()).hasSize(1);
                    assertThat(failure.getSuppressed()[0]).hasMessageContaining("second");
                });
    }

    @Test
    void closingTheBusRemovesSubscribersAndRejectsFurtherPublishing() {
        MarketEventBus bus = new MarketEventBus();
        List<MarketEvent> received = new ArrayList<>();
        bus.subscribe(received::add);

        bus.close();

        assertThat(bus.subscriberCount()).isZero();
        assertThatThrownBy(() -> bus.publish(new SessionExpiredEvent(sessionKey(), "shutdown", NOW)))
                .isInstanceOf(PipelineException.class);
        assertThatThrownBy(() -> bus.subscribe(received::add))
                .isInstanceOf(PipelineException.class);
        assertThat(received).isEmpty();
    }

    @Test
    void nullEventsAreRejectedDeterministically() {
        MarketEventBus bus = new MarketEventBus();
        assertThatThrownBy(() -> bus.publish(null))
                .isInstanceOf(PipelineValidationException.class);
    }

    @Test
    void eventsAreImmutableAndCarryTheirDiscriminator() {
        Map<String, IndicatorResult> results = new HashMap<>();
        results.put("SMA", new IndicatorResult("SMA", "1.0", NOW, IndicatorStatus.OK, false,
                Map.of("value", BigDecimal.ONE), Map.of()));
        Candle candle = PipelineTestFixtures.candle(0, "100");

        IndicatorUpdatedEvent event = new IndicatorUpdatedEvent(sessionKey(), candle, results, NOW);
        results.clear();

        assertThat(event.results()).hasSize(1);
        assertThat(event.type()).isEqualTo(MarketEventType.INDICATOR_UPDATED);
        assertThatThrownBy(() -> event.results().put("EMA", null))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(new TickReceivedEvent(PipelineTestFixtures.tick(
                PipelineTestFixtures.NSE_KEY, 1L, NOW, "100", 10L), NOW).type())
                .isEqualTo(MarketEventType.TICK_RECEIVED);
        assertThat(new CandleCompletedEvent(candle, NOW).type())
                .isEqualTo(MarketEventType.CANDLE_COMPLETED);
        assertThat(new SessionExpiredEvent(sessionKey(), "idle", NOW).type())
                .isEqualTo(MarketEventType.SESSION_EXPIRED);
    }

    @Test
    void sessionLifecycleTransitionsArePublishedAsEvents() {
        MarketEventBus bus = new MarketEventBus();
        List<MarketEvent> received = new ArrayList<>();
        bus.subscribe(received::add);
        IndicatorSessionRegistry registry = new IndicatorSessionRegistry(
                IndicatorEngine.standard(),
                SessionRegistryPolicy.unlimited(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new MarketEventSessionListener(bus, Clock.fixed(NOW, ZoneOffset.UTC)));

        registry.getOrCreate(sessionKey(), Set.of("SMA"));
        registry.remove(sessionKey(), "idle");

        assertThat(received).extracting(MarketEvent::type)
                .containsExactly(MarketEventType.SESSION_STARTED, MarketEventType.SESSION_EXPIRED);
    }

    private static IndicatorSessionKey sessionKey() {
        return new IndicatorSessionKey(
                PipelineTestFixtures.NSE_KEY, Interval.ONE_MINUTE, IndicatorConfig.defaults());
    }
}
