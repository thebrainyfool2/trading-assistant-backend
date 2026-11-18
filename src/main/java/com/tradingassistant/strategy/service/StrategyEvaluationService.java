package com.tradingassistant.strategy.service;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.intelligence.domain.MarketAssessment;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.options.domain.OptionsAssessment;
import com.tradingassistant.pipeline.event.MarketAssessmentGeneratedEvent;
import com.tradingassistant.pipeline.event.MarketEvent;
import com.tradingassistant.pipeline.event.MarketEventListener;
import com.tradingassistant.pipeline.event.MarketEventPublisher;
import com.tradingassistant.pipeline.event.OptionsAssessmentGeneratedEvent;
import com.tradingassistant.pipeline.event.StrategyEvaluatedEvent;
import com.tradingassistant.strategy.config.StrategyEngineConfig;
import com.tradingassistant.strategy.domain.StrategyDefinition;
import com.tradingassistant.strategy.domain.StrategyEvaluation;
import com.tradingassistant.strategy.domain.StrategyEvaluationContext;
import com.tradingassistant.strategy.engine.StrategyEvaluationEngine;
import com.tradingassistant.strategy.exception.StrategyValidationException;
import com.tradingassistant.strategy.registry.StrategyRegistry;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Collections;

/**
 * Evaluates registered strategies when the market or the option chain is
 * described.
 *
 * <p>The service consumes {@link MarketAssessmentGeneratedEvent} and
 * {@link OptionsAssessmentGeneratedEvent} only. A market event is evaluated with
 * the option assessment of the same instant when one is cached; an options event
 * is evaluated with the cached market assessment of the same instant. When the
 * two are not aligned, nothing is evaluated, because a strategy must never
 * compare values from different moments.</p>
 *
 * <p>Cached assessments, the provider staging and the registry are the only
 * state. Each cache is bounded and per instrument, and no strategy definition is
 * ever mutated.</p>
 */
public final class StrategyEvaluationService implements MarketEventListener {

    private static final int MAX_CACHED_ASSESSMENTS = 64;

    private final StrategyEvaluationEngine engine;
    private final StrategyRegistry registry;
    private final StrategyEngineConfig config;
    private final StrategyMarketDataProvider provider;
    private final MarketEventPublisher publisher;
    private final Clock clock;
    private final Map<InstrumentKey, MarketAssessment> marketAssessments =
            boundedCache();
    private final Map<InstrumentKey, OptionsAssessment> optionsAssessments =
            boundedCache();

    public StrategyEvaluationService(StrategyEvaluationEngine engine,
                                     StrategyRegistry registry,
                                     StrategyEngineConfig config,
                                     StrategyMarketDataProvider provider,
                                     MarketEventPublisher publisher,
                                     Clock clock) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void onEvent(MarketEvent event) {
        if (event == null) {
            throw new StrategyValidationException("Event must not be null.");
        }
        if (!config.enabled()) {
            return;
        }
        if (event instanceof MarketAssessmentGeneratedEvent generated) {
            onMarketAssessment(generated.assessment());
        } else if (event instanceof OptionsAssessmentGeneratedEvent generated) {
            onOptionsAssessment(generated.assessment());
        }
    }

    private void onMarketAssessment(MarketAssessment assessment) {
        marketAssessments.put(assessment.instrument(), assessment);
        Optional<OptionsAssessment> options = aligned(
                optionsAssessments.get(assessment.instrument()), assessment.timestamp());
        evaluateAndPublish(context(assessment, options));
    }

    private void onOptionsAssessment(OptionsAssessment assessment) {
        optionsAssessments.put(assessment.spotInstrument(), assessment);
        MarketAssessment market = marketAssessments.get(assessment.spotInstrument());
        if (market == null || !market.timestamp().equals(assessment.timestamp())) {
            return;
        }
        evaluateAndPublish(context(market, Optional.of(assessment)));
    }

    private void evaluateAndPublish(StrategyEvaluationContext context) {
        List<StrategyDefinition> enabled = registry.enabled();
        if (enabled.isEmpty()) {
            return;
        }
        List<StrategyEvaluation> evaluations = engine.evaluate(context, enabled);
        if (evaluations.isEmpty()) {
            return;
        }
        publisher.publish(new StrategyEvaluatedEvent(evaluations, clock.instant()));
    }

    private StrategyEvaluationContext context(MarketAssessment assessment,
                                              Optional<OptionsAssessment> options) {
        InstrumentKey instrument = assessment.instrument();
        Interval interval = assessment.interval();
        Optional<Candle> candle = provider.latestCandle(instrument, interval)
                .filter(value -> value.timestamp().equals(assessment.timestamp()))
                .filter(value -> value.instrument().equals(instrument))
                .filter(value -> value.interval() == interval);
        Map<String, IndicatorResult> indicators = new LinkedHashMap<>();
        provider.latestIndicators(instrument, interval).forEach((type, result) -> {
            if (result != null && result.timestamp().equals(assessment.timestamp())) {
                indicators.put(type, result);
            }
        });
        return new StrategyEvaluationContext(assessment, options, candle,
                Map.copyOf(indicators));
    }

    private static Optional<OptionsAssessment> aligned(OptionsAssessment assessment,
                                                       java.time.Instant timestamp) {
        if (assessment == null || !assessment.timestamp().equals(timestamp)) {
            return Optional.empty();
        }
        return Optional.of(assessment);
    }

    public int cachedMarketAssessments() {
        return marketAssessments.size();
    }

    public int cachedOptionsAssessments() {
        return optionsAssessments.size();
    }

    public void clearCaches() {
        marketAssessments.clear();
        optionsAssessments.clear();
    }

    public StrategyRegistry registry() {
        return registry;
    }

    private static <V> Map<InstrumentKey, V> boundedCache() {
        return Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<InstrumentKey, V> eldest) {
                return size() > MAX_CACHED_ASSESSMENTS;
            }
        });
    }
}
