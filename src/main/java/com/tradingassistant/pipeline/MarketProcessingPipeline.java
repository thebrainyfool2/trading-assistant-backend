package com.tradingassistant.pipeline;

import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.Tick;
import com.tradingassistant.pipeline.aggregation.AggregationPolicy;
import com.tradingassistant.pipeline.aggregation.AggregationResult;
import com.tradingassistant.pipeline.aggregation.CandleAggregator;
import com.tradingassistant.pipeline.calendar.MarketCalendar;
import com.tradingassistant.pipeline.calendar.MarketCalendarRegistry;
import com.tradingassistant.pipeline.event.CandleCompletedEvent;
import com.tradingassistant.pipeline.event.IndicatorUpdatedEvent;
import com.tradingassistant.pipeline.event.MarketEventPublisher;
import com.tradingassistant.pipeline.event.TickReceivedEvent;
import com.tradingassistant.pipeline.exception.PipelineException;
import com.tradingassistant.pipeline.exception.PipelineValidationException;
import com.tradingassistant.pipeline.session.IndicatorSession;
import com.tradingassistant.pipeline.session.IndicatorSessionKey;
import com.tradingassistant.pipeline.session.IndicatorSessionRegistry;
import com.tradingassistant.pipeline.session.IndicatorSessionUpdate;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The runtime pipeline:
 * {@code tick -> aggregator -> completed candle -> indicator session -> event}.
 *
 * <p>Ordering guarantees, identical for live and replay input:</p>
 * <ol>
 *   <li>{@code TickReceivedEvent} for the raw tick,</li>
 *   <li>{@code SessionStartedEvent} when the candle creates a session,</li>
 *   <li>{@code CandleCompletedEvent} for the closed candle,</li>
 *   <li>{@code IndicatorUpdatedEvent} once every indicator advanced.</li>
 * </ol>
 *
 * <p>The pipeline publishes observations and never interprets them: no signal,
 * score, regime or recommendation is produced. Instruments are independent, so
 * ticks for different instruments can be processed concurrently. Everything for
 * one instrument runs under that instrument's channel-group monitor, so a
 * completed candle and its indicator update cannot interleave with another
 * tick's and the published sequence stays strictly ordered.</p>
 */
public final class MarketProcessingPipeline implements AutoCloseable {

    private final IndicatorSessionRegistry sessions;
    private final MarketEventPublisher publisher;
    private final MarketCalendarRegistry calendars;
    private final AggregationPolicy aggregationPolicy;
    private final Clock clock;
    private final ConcurrentHashMap<InstrumentKey, ChannelGroup> channels = new ConcurrentHashMap<>();
    private final Object registrationLock = new Object();

    private volatile boolean closed;

    public MarketProcessingPipeline(
            IndicatorSessionRegistry sessions,
            MarketEventPublisher publisher,
            MarketCalendarRegistry calendars,
            AggregationPolicy aggregationPolicy,
            Clock clock) {
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
        this.calendars = Objects.requireNonNull(calendars, "calendars must not be null");
        this.aggregationPolicy = Objects.requireNonNull(aggregationPolicy, "aggregationPolicy must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Registers an instrument and interval. Subscribing the same session key
     * twice is idempotent so duplicate wiring cannot double-count volume.
     */
    public void subscribe(PipelineSubscription subscription) {
        Objects.requireNonNull(subscription, "subscription must not be null");
        if (closed) {
            throw new PipelineException("The market processing pipeline is closed");
        }
        Set<String> types = sessions.requireTypes(subscription.indicatorTypes());
        MarketCalendar calendar = calendars.get(subscription.instrument().exchange());

        synchronized (registrationLock) {
            ChannelGroup group = channels.get(subscription.instrument());
            List<Channel> current = group == null ? List.of() : group.channels();
            IndicatorSessionKey sessionKey = subscription.sessionKey();
            for (Channel channel : current) {
                if (channel.sessionKey().equals(sessionKey)) {
                    return;
                }
            }
            Channel channel = new Channel(
                    sessionKey,
                    CandleAggregator.create(
                            subscription.instrument(),
                            subscription.interval(),
                            calendar,
                            aggregationPolicy,
                            clock),
                    types);
            List<Channel> updated = new ArrayList<>(current);
            updated.add(channel);
            channels.put(subscription.instrument(), new ChannelGroup(List.copyOf(updated)));
        }
    }

    /**
     * Removes every channel for an instrument. Forming candles are discarded:
     * an unsubscribed instrument is no longer tracked.
     */
    public boolean unsubscribe(InstrumentKey instrument) {
        if (instrument == null) {
            return false;
        }
        synchronized (registrationLock) {
            return channels.remove(instrument) != null;
        }
    }

    public Set<InstrumentKey> subscribedInstruments() {
        return Set.copyOf(new LinkedHashSet<>(channels.keySet()));
    }

    public int channelCount() {
        int count = 0;
        for (ChannelGroup group : channels.values()) {
            count += group.channels().size();
        }
        return count;
    }

    /**
     * Processes one tick. Ticks for an unsubscribed instrument publish only the
     * tick event, which keeps the raw feed observable.
     */
    public PipelineOutcome process(Tick tick) {
        if (tick == null) {
            throw new PipelineValidationException("tick must not be null");
        }
        if (closed) {
            throw new PipelineException("The market processing pipeline is closed");
        }
        ChannelGroup group = channels.get(tick.instrument());
        if (group == null) {
            publisher.publish(new TickReceivedEvent(tick, clock.instant()));
            return new PipelineOutcome(0, 0, 0, 1);
        }

        synchronized (group) {
            publisher.publish(new TickReceivedEvent(tick, clock.instant()));
            int accepted = 0;
            int candles = 0;
            int updated = 0;
            int events = 1;
            for (Channel channel : group.channels()) {
                AggregationResult result = channel.aggregator().accept(tick);
                if (!result.isAccepted()) {
                    continue;
                }
                accepted++;
                for (Candle candle : result.completedCandles()) {
                    publishCandle(channel, candle);
                    candles++;
                    updated++;
                    events += 2;
                }
            }
            return new PipelineOutcome(accepted, candles, updated, events);
        }
    }

    /**
     * Closes the forming candle for every interval of one instrument, which is
     * the correct behaviour at session end and before shutdown.
     */
    public List<Candle> flush(InstrumentKey instrument) {
        if (instrument == null) {
            return List.of();
        }
        ChannelGroup group = channels.get(instrument);
        if (group == null) {
            return List.of();
        }
        List<Candle> flushed = new ArrayList<>();
        synchronized (group) {
            for (Channel channel : group.channels()) {
                channel.aggregator().flush().ifPresent(candle -> {
                    publishCandle(channel, candle);
                    flushed.add(candle);
                });
            }
        }
        return List.copyOf(flushed);
    }

    /**
     * Closes every forming candle and returns the candles that were published.
     */
    public List<Candle> flushAll() {
        List<Candle> flushed = new ArrayList<>();
        for (InstrumentKey instrument : channels.keySet()) {
            flushed.addAll(flush(instrument));
        }
        return List.copyOf(flushed);
    }

    public Optional<CandleAggregator> aggregator(InstrumentKey instrument, com.tradingassistant.market.domain.Interval interval) {
        if (instrument == null || interval == null) {
            return Optional.empty();
        }
        ChannelGroup group = channels.get(instrument);
        if (group == null) {
            return Optional.empty();
        }
        return group.channels().stream()
                .filter(channel -> channel.sessionKey().interval() == interval)
                .map(Channel::aggregator)
                .findFirst();
    }

    /**
     * Flushes forming candles and stops accepting ticks. Sessions are expired by
     * the lifecycle manager, which owns shutdown ordering.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        flushAll();
    }

    private void publishCandle(Channel channel, Candle candle) {
        publisher.publish(new CandleCompletedEvent(candle, clock.instant()));
        IndicatorSession session = sessions.getOrCreate(channel.sessionKey(), channel.indicatorTypes());
        IndicatorSessionUpdate update = session.update(candle, clock.instant());
        publisher.publish(new IndicatorUpdatedEvent(
                update.session(), candle, update.results(), clock.instant()));
    }

    /**
     * Immutable channels for one instrument plus the monitor that serializes
     * its ticks. Registration replaces the group; a tick in flight keeps the
     * group it started with.
     */
    private record ChannelGroup(List<Channel> channels) {
    }

    private record Channel(
            IndicatorSessionKey sessionKey,
            CandleAggregator aggregator,
            Set<String> indicatorTypes) {
    }
}
