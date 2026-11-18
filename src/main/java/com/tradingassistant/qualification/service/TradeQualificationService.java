package com.tradingassistant.qualification.service;

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
import com.tradingassistant.pipeline.event.TradeQualifiedEvent;
import com.tradingassistant.qualification.config.TradeQualificationConfig;
import com.tradingassistant.qualification.domain.QualificationKey;
import com.tradingassistant.qualification.domain.QualificationLedger;
import com.tradingassistant.qualification.domain.QualificationStatus;
import com.tradingassistant.qualification.domain.TradeQualification;
import com.tradingassistant.qualification.domain.TradeQualificationContext;
import com.tradingassistant.qualification.engine.TradeQualificationEngine;
import com.tradingassistant.qualification.exception.QualificationValidationException;
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
 * Qualifies strategy evaluations when they are published.
 *
 * <p>The service consumes {@link StrategyEvaluatedEvent} and the two assessment
 * events. Qualification needs the market assessment of the evaluated instant, so
 * an evaluation that arrives before its assessment is staged in a bounded pending
 * map and qualified when the assessment arrives. That makes the result independent
 * of bus subscription order and of the nested publication the strategy service
 * performs from its own assessment handler.</p>
 *
 * <p>Only aligned data is used: an option assessment of another instant is not
 * attached, and a candle of another instant is ignored. The caches, the pending
 * staging and the ledger are bounded, so a long-running process cannot grow without
 * limit. Nothing is persisted.</p>
 */
public final class TradeQualificationService implements MarketEventListener {

    private static final int MAX_CACHED_ASSESSMENTS = 64;
    private static final int MAX_PENDING_KEYS = 64;

    private final TradeQualificationEngine engine;
    private final TradeQualificationConfig config;
    private final QualificationLedger ledger;
    private final StrategyMarketDataProvider provider;
    private final MarketEventPublisher publisher;
    private final Clock clock;
    private final Map<InstrumentKey, MarketAssessment> marketAssessments = boundedCache();
    private final Map<InstrumentKey, OptionsAssessment> optionsAssessments = boundedCache();
    private final Map<PendingKey, List<StrategyEvaluation>> pending =
            Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<PendingKey, List<StrategyEvaluation>> eldest) {
                    return size() > MAX_PENDING_KEYS;
                }
            });
    private long suppressedDuplicates;

    public TradeQualificationService(TradeQualificationEngine engine,
                                     TradeQualificationConfig config,
                                     QualificationLedger ledger,
                                     StrategyMarketDataProvider provider,
                                     MarketEventPublisher publisher,
                                     Clock clock) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.ledger = Objects.requireNonNull(ledger, "ledger must not be null");
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void onEvent(MarketEvent event) {
        if (event == null) {
            throw new QualificationValidationException("Event must not be null.");
        }
        if (!config.enabled()) {
            return;
        }
        if (event instanceof MarketAssessmentGeneratedEvent generated) {
            onMarketAssessment(generated.assessment());
        } else if (event instanceof OptionsAssessmentGeneratedEvent generated) {
            onOptionsAssessment(generated.assessment());
        } else if (event instanceof StrategyEvaluatedEvent generated) {
            onStrategyEvaluations(generated.evaluations());
        }
    }

    private void onMarketAssessment(MarketAssessment assessment) {
        marketAssessments.put(assessment.instrument(), assessment);
        List<StrategyEvaluation> waiting = pending.remove(new PendingKey(
                assessment.instrument(), assessment.timestamp(), assessment.interval()));
        if (waiting == null || waiting.isEmpty()) {
            return;
        }
        qualifyAll(List.copyOf(waiting), assessment);
    }

    private void onOptionsAssessment(OptionsAssessment assessment) {
        optionsAssessments.put(assessment.spotInstrument(), assessment);
    }

    private void onStrategyEvaluations(List<StrategyEvaluation> evaluations) {
        Map<PendingKey, List<StrategyEvaluation>> batches = new LinkedHashMap<>();
        for (StrategyEvaluation evaluation : evaluations) {
            PendingKey key = new PendingKey(evaluation.instrument(), evaluation.timestamp(),
                    evaluation.interval());
            batches.computeIfAbsent(key, ignored -> new ArrayList<>()).add(evaluation);
        }
        batches.forEach((key, batch) -> {
            MarketAssessment market = marketAssessments.get(key.instrument());
            if (market == null
                    || !market.timestamp().equals(key.timestamp())
                    || market.interval() != key.interval()) {
                pending.computeIfAbsent(key, ignored -> new ArrayList<>()).addAll(batch);
                return;
            }
            qualifyAll(List.copyOf(batch), market);
        });
    }

    private void qualifyAll(List<StrategyEvaluation> evaluations, MarketAssessment market) {
        List<TradeQualification> qualifications = new ArrayList<>(evaluations.size());
        for (StrategyEvaluation evaluation : evaluations) {
            QualificationKey key = new QualificationKey(evaluation.strategyId(),
                    evaluation.instrument(), evaluation.timestamp());
            if (ledger.contains(key)) {
                suppressedDuplicates++;
                continue;
            }
            TradeQualification qualification = engine.evaluate(context(evaluation, market));
            if (qualification.status() == QualificationStatus.QUALIFIED) {
                ledger.record(key);
            }
            qualifications.add(qualification);
        }
        if (qualifications.isEmpty()) {
            return;
        }
        publisher.publish(new TradeQualifiedEvent(List.copyOf(qualifications), clock.instant()));
    }

    private TradeQualificationContext context(StrategyEvaluation evaluation,
                                              MarketAssessment market) {
        InstrumentKey instrument = evaluation.instrument();
        Interval interval = evaluation.interval();
        OptionsAssessment options = optionsAssessments.get(instrument);
        if (options != null && !options.timestamp().equals(evaluation.timestamp())) {
            options = null;
        }
        Optional<Candle> candle = provider.latestCandle(instrument, interval)
                .filter(value -> value.timestamp().equals(evaluation.timestamp()))
                .filter(value -> value.instrument().equals(instrument))
                .filter(value -> value.interval() == interval);
        return TradeQualificationContext.of(evaluation, market, options, candle.orElse(null));
    }

    public int cachedMarketAssessments() {
        return marketAssessments.size();
    }

    public int cachedOptionsAssessments() {
        return optionsAssessments.size();
    }

    public int pendingEvaluations() {
        return pending.values().stream().mapToInt(List::size).sum();
    }

    public long suppressedDuplicates() {
        return suppressedDuplicates;
    }

    public QualificationLedger ledger() {
        return ledger;
    }

    public TradeQualificationEngine engine() {
        return engine;
    }

    /**
     * Clears the staged assessments and pending evaluations. The qualification
     * ledger is not cleared: it is the duplicate memory, and it has its own reset.
     */
    public void clearCaches() {
        marketAssessments.clear();
        optionsAssessments.clear();
        pending.clear();
    }

    private static <V> Map<InstrumentKey, V> boundedCache() {
        return Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<InstrumentKey, V> eldest) {
                return size() > MAX_CACHED_ASSESSMENTS;
            }
        });
    }

    /**
     * Identity of a staged evaluation batch: instrument, instant and interval.
     */
    private record PendingKey(InstrumentKey instrument, Instant timestamp, Interval interval) {
    }
}
