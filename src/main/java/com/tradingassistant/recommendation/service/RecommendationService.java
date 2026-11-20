package com.tradingassistant.recommendation.service;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.intelligence.domain.MarketAssessment;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.options.domain.ChainSnapshot;
import com.tradingassistant.options.domain.OptionsAssessment;
import com.tradingassistant.options.service.OptionChainProvider;
import com.tradingassistant.pipeline.event.MarketAssessmentGeneratedEvent;
import com.tradingassistant.pipeline.event.MarketEvent;
import com.tradingassistant.pipeline.event.MarketEventListener;
import com.tradingassistant.pipeline.event.OptionsAssessmentGeneratedEvent;
import com.tradingassistant.pipeline.event.RiskAssessmentGeneratedEvent;
import com.tradingassistant.pipeline.event.SessionExpiredEvent;
import com.tradingassistant.pipeline.event.StrategyEvaluatedEvent;
import com.tradingassistant.pipeline.event.TradeQualifiedEvent;
import com.tradingassistant.platform.readmodel.MarketDataFreshnessService;
import com.tradingassistant.qualification.domain.TradeQualification;
import com.tradingassistant.recommendation.config.RecommendationConfig;
import com.tradingassistant.recommendation.domain.Recommendation;
import com.tradingassistant.recommendation.domain.RecommendationContext;
import com.tradingassistant.recommendation.domain.RecommendationKey;
import com.tradingassistant.recommendation.engine.RecommendationEngine;
import com.tradingassistant.recommendation.event.RecommendationEventPublisher;
import com.tradingassistant.recommendation.event.RecommendationExpiredEvent;
import com.tradingassistant.recommendation.event.RecommendationGeneratedEvent;
import com.tradingassistant.recommendation.event.RecommendationUpdatedEvent;
import com.tradingassistant.recommendation.exception.RecommendationValidationException;
import com.tradingassistant.risk.domain.RiskAssessment;
import com.tradingassistant.strategy.domain.StrategyEvaluation;
import com.tradingassistant.strategy.service.StrategyMarketDataProvider;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Live bridge between the existing engine events and the pure recommendation
 * engine.
 *
 * <p>The service stages aligned inputs by instrument and market timestamp,
 * evaluates only when all required evidence is present, and publishes its own
 * events. It does not execute, submit, monitor or size anything. A stale or
 * incomplete candidate is skipped rather than allowed to break the synchronous
 * market event bus.</p>
 */
public final class RecommendationService implements MarketEventListener {

    private static final int MAX_CACHED_ENTRIES = 64;

    private final RecommendationEngine engine;
    private final RecommendationConfig config;
    private final RecommendationEventPublisher publisher;
    private final OptionChainProvider chainProvider;
    private final StrategyMarketDataProvider marketDataProvider;
    private final MarketDataFreshnessService freshnessService;
    private final Clock clock;
    private final Map<InstrumentKey, MarketAssessment> marketAssessments =
            bounded();
    private final Map<InstrumentKey, OptionsAssessment> optionsAssessments =
            bounded();
    private final Map<RecommendationKey, List<StrategyEvaluation>> strategies =
            bounded();
    private final Map<RecommendationKey, List<TradeQualification>> qualifications =
            bounded();
    private final Map<RecommendationKey, List<RiskAssessment>> risks = bounded();
    private final Map<RecommendationKey, Recommendation> published = bounded();

    public RecommendationService(RecommendationEngine engine,
                                 RecommendationConfig config,
                                 RecommendationEventPublisher publisher,
                                 OptionChainProvider chainProvider,
                                 StrategyMarketDataProvider marketDataProvider,
                                 MarketDataFreshnessService freshnessService,
                                 Clock clock) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
        this.chainProvider = Objects.requireNonNull(chainProvider,
                "chainProvider must not be null");
        this.marketDataProvider = Objects.requireNonNull(marketDataProvider,
                "marketDataProvider must not be null");
        this.freshnessService = Objects.requireNonNull(freshnessService,
                "freshnessService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void onEvent(MarketEvent event) {
        if (event == null) {
            throw new RecommendationValidationException("event must not be null");
        }
        if (!config.enabled()) {
            return;
        }
        if (event instanceof MarketAssessmentGeneratedEvent generated) {
            MarketAssessment assessment = generated.assessment();
            marketAssessments.put(assessment.instrument(), assessment);
            attemptInstrument(assessment.instrument(), assessment.interval());
        } else if (event instanceof OptionsAssessmentGeneratedEvent generated) {
            OptionsAssessment assessment = generated.assessment();
            optionsAssessments.put(assessment.spotInstrument(), assessment);
            attemptInstrument(assessment.spotInstrument(), null);
        } else if (event instanceof StrategyEvaluatedEvent generated) {
            cacheStrategies(generated.evaluations());
        } else if (event instanceof TradeQualifiedEvent generated) {
            cacheQualifications(generated.qualifications());
        } else if (event instanceof RiskAssessmentGeneratedEvent generated) {
            cacheRisks(generated.assessments());
        } else if (event instanceof SessionExpiredEvent generated) {
            expire(generated.session().instrument(), generated.session().interval(),
                    generated.reason());
        }
    }

    public Optional<Recommendation> current(RecommendationKey key) {
        return Optional.ofNullable(published.get(key));
    }

    /**
     * Latest recommendation for an instrument and interval, independent of the
     * caller knowing the exact market timestamp.
     */
    public Optional<Recommendation> latest(InstrumentKey instrument, Interval interval) {
        Objects.requireNonNull(instrument, "instrument must not be null");
        Objects.requireNonNull(interval, "interval must not be null");
        synchronized (published) {
            return published.entrySet().stream()
                    .filter(entry -> entry.getKey().instrument().equals(instrument)
                            && entry.getKey().interval() == interval)
                    .max(java.util.Map.Entry.comparingByKey(
                            java.util.Comparator.comparing(RecommendationKey::timestamp)))
                    .map(java.util.Map.Entry::getValue);
        }
    }

    /**
     * The recommendation for one exact market timestamp. Recording a manual fill
     * uses this instead of {@link #latest} so the plan it derives cannot silently
     * move to a newer recommendation after the owner has acted.
     */
    public Optional<Recommendation> at(InstrumentKey instrument, Interval interval,
                                       Instant timestamp) {
        Objects.requireNonNull(instrument, "instrument must not be null");
        Objects.requireNonNull(interval, "interval must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        return Optional.ofNullable(published.get(
                new RecommendationKey(instrument, interval, timestamp)));
    }

    /**
     * Latest aligned context for an instrument and interval. Monitoring uses this
     * to reuse the staged Phase 14 inputs without recalculating them.
     */
    public Optional<RecommendationContext> latestContext(InstrumentKey instrument,
                                                         Interval interval) {
        return latest(instrument, interval).flatMap(recommendation -> contextAt(
                recommendation.instrument(), recommendation.interval(),
                recommendation.timestamp()));
    }

    /**
     * Aligned context for one exact recommendation timestamp. The chain snapshot
     * is requested for that timestamp, so a pinned fill reproduces the plan the
     * owner saw rather than the newest one.
     */
    public Optional<RecommendationContext> contextAt(InstrumentKey instrument,
                                                     Interval interval,
                                                     Instant timestamp) {
        return at(instrument, interval, timestamp).flatMap(recommendation -> {
            RecommendationKey key = keyOf(recommendation.instrument(),
                    recommendation.interval(), recommendation.timestamp());
            return buildContext(key, chainProvider.snapshotFor(key.instrument(),
                    key.timestamp()));
        });
    }

    /**
     * Every current instrument/interval recommendation, newest first. The
     * published map can briefly contain an older timestamp while a newer one is
     * being staged, so this collapses to one entry per instrument and interval.
     */
    public List<Recommendation> latestAll() {
        synchronized (published) {
            Map<String, Recommendation> latest = new LinkedHashMap<>();
            for (Map.Entry<RecommendationKey, Recommendation> entry
                    : published.entrySet()) {
                RecommendationKey key = entry.getKey();
                String identity = key.instrument().segment().name() + ':'
                        + key.instrument().symbolToken() + ':' + key.interval().name();
                Recommendation current = latest.get(identity);
                if (current == null
                        || key.timestamp().isAfter(current.timestamp())) {
                    latest.put(identity, entry.getValue());
                }
            }
            return latest.values().stream()
                    .sorted(java.util.Comparator
                            .comparing(Recommendation::timestamp).reversed()
                            .thenComparing(recommendation ->
                                    recommendation.instrument().segment().name())
                            .thenComparing(recommendation ->
                                    recommendation.instrument().symbolToken()))
                    .toList();
        }
    }

    public int publishedCount() {
        return published.size();
    }

    public void expire(InstrumentKey instrument, Interval interval, String reason) {
        Objects.requireNonNull(instrument, "instrument must not be null");
        Objects.requireNonNull(interval, "interval must not be null");
        String expiryReason = reason == null || reason.isBlank()
                ? "session expired" : reason;
        for (RecommendationKey key : List.copyOf(published.keySet())) {
            if (key.instrument().equals(instrument) && key.interval() == interval) {
                published.remove(key);
                publisher.publish(new RecommendationExpiredEvent(key, expiryReason,
                        clock.instant()));
            }
        }
    }

    public void clearCaches() {
        marketAssessments.clear();
        optionsAssessments.clear();
        strategies.clear();
        qualifications.clear();
        risks.clear();
        published.clear();
    }

    private void cacheStrategies(List<StrategyEvaluation> evaluations) {
        Map<RecommendationKey, List<StrategyEvaluation>> batches = new LinkedHashMap<>();
        for (StrategyEvaluation evaluation : evaluations) {
            batches.computeIfAbsent(keyOf(evaluation.instrument(), evaluation.interval(),
                            evaluation.timestamp()), ignored -> new ArrayList<>())
                    .add(evaluation);
        }
        batches.forEach((key, batch) -> {
            strategies.put(key, List.copyOf(batch));
            attempt(key);
        });
    }

    private void cacheQualifications(List<TradeQualification> values) {
        Map<RecommendationKey, List<TradeQualification>> batches = new LinkedHashMap<>();
        for (TradeQualification value : values) {
            batches.computeIfAbsent(keyOf(value.instrument(), value.interval(),
                            value.timestamp()), ignored -> new ArrayList<>())
                    .add(value);
        }
        batches.forEach((key, batch) -> {
            qualifications.put(key, List.copyOf(batch));
            attempt(key);
        });
    }

    private void cacheRisks(List<RiskAssessment> assessments) {
        Map<RecommendationKey, List<RiskAssessment>> batches = new LinkedHashMap<>();
        for (RiskAssessment assessment : assessments) {
            batches.computeIfAbsent(keyOf(assessment.instrument(), assessment.interval(),
                            assessment.timestamp()), ignored -> new ArrayList<>())
                    .add(assessment);
        }
        batches.forEach((key, batch) -> {
            risks.put(key, List.copyOf(batch));
            attempt(key);
        });
    }

    private void attemptInstrument(InstrumentKey instrument, Interval interval) {
        List<RecommendationKey> keys = new ArrayList<>();
        keys.addAll(risks.keySet());
        keys.addAll(qualifications.keySet());
        keys.addAll(strategies.keySet());
        for (RecommendationKey key : List.copyOf(keys)) {
            if (key.instrument().equals(instrument)
                    && (interval == null || key.interval() == interval)) {
                attempt(key);
            }
        }
    }

    private void attempt(RecommendationKey key) {
        Optional<ChainSnapshot> chain = chainProvider.snapshotFor(key.instrument(),
                key.timestamp());
        if (chain.isEmpty()) {
            // Wait for the options stage rather than publishing a premature NO_TRADE
            // while the pipeline is still completing the same market timestamp.
            return;
        }
        Optional<RecommendationContext> built = buildContext(key, chain);
        if (built.isEmpty()) {
            return;
        }
        RecommendationContext context = built.get();
        Recommendation recommendation;
        try {
            recommendation = engine.evaluate(context);
        } catch (RecommendationValidationException rejected) {
            return;
        }
        publish(recommendation);
    }

    private Optional<RecommendationContext> buildContext(RecommendationKey key,
                                                         Optional<ChainSnapshot> chain) {
        MarketAssessment market = marketAssessments.get(key.instrument());
        if (market == null || !market.timestamp().equals(key.timestamp())
                || market.interval() != key.interval()) {
            return Optional.empty();
        }
        List<TradeQualification> qualificationBatch = qualifications.get(key);
        List<RiskAssessment> riskBatch = risks.get(key);
        if (qualificationBatch == null || qualificationBatch.isEmpty()
                || riskBatch == null || riskBatch.isEmpty()) {
            return Optional.empty();
        }
        Optional<MarketDataFreshnessService.Freshness> freshness =
                freshnessService.freshness(key.instrument());
        if (freshness.isEmpty()) {
            return Optional.empty();
        }
        OptionsAssessment options = optionsAssessments.get(key.instrument());
        if (options != null && !options.timestamp().equals(key.timestamp())) {
            options = null;
        }
        if (options == null) {
            return Optional.empty();
        }
        Optional<Candle> candle = marketDataProvider.latestCandle(key.instrument(),
                        key.interval())
                .filter(value -> value.timestamp().equals(key.timestamp()))
                .filter(value -> value.instrument().equals(key.instrument()))
                .filter(value -> value.interval() == key.interval());
        Map<String, IndicatorResult> indicators = new LinkedHashMap<>();
        marketDataProvider.latestIndicators(key.instrument(), key.interval())
                .forEach((type, result) -> {
                    if (result != null && result.timestamp().equals(key.timestamp())) {
                        indicators.put(type, result);
                    }
                });
        return Optional.of(new RecommendationContext(market,
                Optional.ofNullable(options), chain, Map.copyOf(indicators), candle,
                strategies.getOrDefault(key, List.of()),
                qualificationBatch, riskBatch, freshness.get()));
    }

    private void publish(Recommendation recommendation) {
        RecommendationKey key = keyOf(recommendation.instrument(),
                recommendation.interval(), recommendation.timestamp());
        for (RecommendationKey existing : List.copyOf(published.keySet())) {
            if (existing.instrument().equals(key.instrument())
                    && existing.interval() == key.interval()
                    && !existing.timestamp().equals(key.timestamp())) {
                published.remove(existing);
                publisher.publish(new RecommendationExpiredEvent(existing,
                        "superseded by a newer market timestamp", clock.instant()));
            }
        }
        Recommendation previous = published.get(key);
        published.put(key, recommendation);
        if (previous == null) {
            publisher.publish(new RecommendationGeneratedEvent(recommendation,
                    clock.instant()));
        } else if (!previous.equals(recommendation)) {
            publisher.publish(new RecommendationUpdatedEvent(recommendation,
                    clock.instant()));
        }
    }

    private static RecommendationKey keyOf(InstrumentKey instrument, Interval interval,
                                           java.time.Instant timestamp) {
        return new RecommendationKey(instrument, interval, timestamp);
    }

    private static <K, V> Map<K, V> bounded() {
        return Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > MAX_CACHED_ENTRIES;
            }
        });
    }
}
