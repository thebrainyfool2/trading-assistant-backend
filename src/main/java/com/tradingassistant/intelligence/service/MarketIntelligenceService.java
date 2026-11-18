package com.tradingassistant.intelligence.service;

import com.tradingassistant.intelligence.config.MarketIntelligenceConfig;
import com.tradingassistant.intelligence.domain.AnalysisContext;
import com.tradingassistant.intelligence.domain.MarketAssessment;
import com.tradingassistant.intelligence.engine.MarketIntelligenceEngine;
import com.tradingassistant.pipeline.event.MarketAssessmentGeneratedEvent;
import com.tradingassistant.intelligence.exception.IntelligenceValidationException;
import com.tradingassistant.intelligence.session.AnalysisKey;
import com.tradingassistant.intelligence.session.MarketIntelligenceHistory;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.pipeline.calendar.MarketCalendar;
import com.tradingassistant.pipeline.calendar.MarketCalendarRegistry;
import com.tradingassistant.pipeline.event.IndicatorUpdatedEvent;
import com.tradingassistant.pipeline.event.MarketEvent;
import com.tradingassistant.pipeline.event.MarketEventListener;
import com.tradingassistant.pipeline.event.MarketEventPublisher;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns pipeline indicator updates into market assessments.
 *
 * <p>The service is the only stateful part of the intelligence layer, and its
 * state is a bounded per-instrument history plus its configuration. Events for
 * instruments are independent, so the service is safe to call from the pipeline
 * thread and from several feed threads at once.</p>
 *
 * <p>Only {@link IndicatorUpdatedEvent} is consumed. Every other pipeline event
 * is ignored, so adding intelligence never changes another event's meaning.</p>
 */
public final class MarketIntelligenceService implements MarketEventListener {

    private final MarketIntelligenceEngine engine;
    private final MarketIntelligenceConfig config;
    private final MarketCalendarRegistry calendars;
    private final MarketEventPublisher publisher;
    private final Clock clock;
    private final ConcurrentHashMap<AnalysisKey, MarketIntelligenceHistory> histories =
            new ConcurrentHashMap<>();

    public MarketIntelligenceService(
            MarketIntelligenceEngine engine,
            MarketIntelligenceConfig config,
            MarketCalendarRegistry calendars,
            MarketEventPublisher publisher,
            Clock clock) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.calendars = Objects.requireNonNull(calendars, "calendars must not be null");
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void onEvent(MarketEvent event) {
        if (event == null) {
            throw new IntelligenceValidationException("Event must not be null.");
        }
        if (!(event instanceof IndicatorUpdatedEvent update)) {
            return;
        }
        Candle candle = update.candle();
        AnalysisKey key = new AnalysisKey(candle.instrument(), candle.interval());
        MarketCalendar calendar = calendars.get(key.instrument().exchange());
        MarketIntelligenceHistory history = histories.computeIfAbsent(
                key, requested -> new MarketIntelligenceHistory(requested, config.historyWindow()));

        AnalysisContext context = history.append(update, calendar);
        MarketAssessment assessment = engine.assess(context);
        publisher.publish(new MarketAssessmentGeneratedEvent(assessment, clock.instant()));
    }

    public int historyCount() {
        return histories.size();
    }

    public Set<AnalysisKey> keys() {
        return Set.copyOf(histories.keySet());
    }

    public Optional<MarketIntelligenceHistory> history(AnalysisKey key) {
        return key == null ? Optional.empty() : Optional.ofNullable(histories.get(key));
    }

    /**
     * Drops one instrument's history. Used when an instrument is unsubscribed
     * and by recovery code that prefers a clean replay.
     */
    public boolean remove(AnalysisKey key) {
        if (key == null) {
            return false;
        }
        return histories.remove(key) != null;
    }

    /**
     * Drops every history. The engine and configuration are unaffected.
     */
    public void clear() {
        histories.clear();
    }

    public MarketIntelligenceConfig config() {
        return config;
    }
}
