package com.tradingassistant.platform.readmodel;

import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.pipeline.calendar.MarketCalendar;
import com.tradingassistant.pipeline.calendar.MarketCalendarRegistry;
import com.tradingassistant.pipeline.event.CandleCompletedEvent;
import com.tradingassistant.pipeline.event.MarketEvent;
import com.tradingassistant.pipeline.event.MarketEventListener;
import com.tradingassistant.pipeline.event.TickReceivedEvent;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded observation of the latest market events used to answer one question:
 * is the data behind a decision current enough to trust?
 *
 * <p>This is an operational projection, not an engine input. It records the last
 * tick and completed candle per instrument, evaluates age against explicit
 * policies and reports whether the exchange calendar currently says the market
 * is open. A later decision layer can use the same service as a gate without
 * duplicating freshness rules.</p>
 */
@Component
public class MarketDataFreshnessService implements MarketEventListener {

    private final Clock clock;
    private final Duration maxTickAge;
    private final Duration maxCandleAge;
    private final ObjectProvider<MarketCalendarRegistry> calendars;
    private final ConcurrentHashMap<InstrumentKey, Observation> observations =
            new ConcurrentHashMap<>();

    public MarketDataFreshnessService(
            Clock clock,
            @Value("${app.live.freshness.max-tick-age:2m}") Duration maxTickAge,
            @Value("${app.live.freshness.max-candle-age:10m}") Duration maxCandleAge,
            ObjectProvider<MarketCalendarRegistry> calendars) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.maxTickAge = positive(maxTickAge, "maxTickAge");
        this.maxCandleAge = positive(maxCandleAge, "maxCandleAge");
        this.calendars = Objects.requireNonNull(calendars, "calendars must not be null");
    }

    @Override
    public void onEvent(MarketEvent event) {
        if (event instanceof TickReceivedEvent tick) {
            observations.compute(tick.tick().instrument(), (key, current) ->
                    current == null
                            ? new Observation(tick.occurredAt(), tick.tick().lastTradedPrice(),
                            null, null)
                            : current.withTick(tick.occurredAt(),
                            tick.tick().lastTradedPrice()));
        } else if (event instanceof CandleCompletedEvent candle) {
            observations.compute(candle.candle().instrument(), (key, current) ->
                    current == null
                            ? new Observation(null, null, candle.candle().timestamp(),
                            candle.candle().interval())
                            : current.withCandle(candle.candle().timestamp(),
                            candle.candle().interval()));
        }
    }

    public List<Freshness> snapshot() {
        Instant now = clock.instant();
        MarketCalendarRegistry registry = calendars.getIfAvailable();
        return observations.entrySet().stream()
                .map(entry -> describe(entry.getKey(), entry.getValue(), now, registry))
                .sorted(Comparator
                        .comparing((Freshness value) -> value.instrument().segment().name())
                        .thenComparing(value -> value.instrument().symbolToken()))
                .toList();
    }

    public Optional<Freshness> freshness(InstrumentKey instrument) {
        if (instrument == null) {
            return Optional.empty();
        }
        Observation observation = observations.get(instrument);
        if (observation == null) {
            return Optional.empty();
        }
        return Optional.of(describe(instrument, observation, clock.instant(),
                calendars.getIfAvailable()));
    }

    public int observedInstruments() {
        return observations.size();
    }

    public void clear() {
        observations.clear();
    }

    private Freshness describe(InstrumentKey instrument, Observation observation, Instant now,
                               MarketCalendarRegistry registry) {
        Long tickAge = ageSeconds(observation.lastTickAt(), now);
        Long candleAge = ageSeconds(observation.lastCandleAt(), now);
        FreshnessStatus status = status(tickAge, candleAge);
        MarketSessionState session = sessionState(instrument, now, registry);
        return new Freshness(instrument, status, observation.lastTickAt(),
                observation.lastCandleAt(), observation.candleInterval(), tickAge,
                candleAge, observation.latestPrice(), session);
    }

    private FreshnessStatus status(Long tickAge, Long candleAge) {
        if (tickAge == null && candleAge == null) {
            return FreshnessStatus.UNKNOWN;
        }
        if (tickAge != null && tickAge > maxTickAge.toSeconds()) {
            return FreshnessStatus.STALE;
        }
        if (candleAge != null && candleAge > maxCandleAge.toSeconds()) {
            return FreshnessStatus.STALE;
        }
        return FreshnessStatus.FRESH;
    }

    private MarketSessionState sessionState(InstrumentKey instrument, Instant now,
                                             MarketCalendarRegistry registry) {
        if (registry == null) {
            return MarketSessionState.UNKNOWN;
        }
        Optional<MarketCalendar> calendar = registry.find(instrument.exchange());
        if (calendar.isEmpty()) {
            return MarketSessionState.UNKNOWN;
        }
        return calendar.get().isTradingTime(now)
                ? MarketSessionState.OPEN : MarketSessionState.CLOSED;
    }

    private static Long ageSeconds(Instant timestamp, Instant now) {
        if (timestamp == null) {
            return null;
        }
        long seconds = Duration.between(timestamp, now).toSeconds();
        return Math.max(0L, seconds);
    }

    private static Duration positive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private record Observation(Instant lastTickAt, BigDecimal latestPrice,
                               Instant lastCandleAt, Interval candleInterval) {

        Observation withTick(Instant at, BigDecimal price) {
            return new Observation(at, price, lastCandleAt, candleInterval);
        }

        Observation withCandle(Instant at, Interval interval) {
            return new Observation(lastTickAt, latestPrice, at, interval);
        }
    }

    public enum FreshnessStatus {
        FRESH,
        STALE,
        UNKNOWN
    }

    public enum MarketSessionState {
        OPEN,
        CLOSED,
        UNKNOWN
    }

    /**
     * One instrument's current freshness observation.
     */
    public record Freshness(
            InstrumentKey instrument,
            FreshnessStatus status,
            Instant lastTickAt,
            Instant lastCandleAt,
            Interval candleInterval,
            Long tickAgeSeconds,
            Long candleAgeSeconds,
            BigDecimal latestPrice,
            MarketSessionState session) {
    }
}
