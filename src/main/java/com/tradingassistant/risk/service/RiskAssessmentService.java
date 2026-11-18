package com.tradingassistant.risk.service;

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
import com.tradingassistant.pipeline.event.RiskAssessmentGeneratedEvent;
import com.tradingassistant.pipeline.event.StrategyEvaluatedEvent;
import com.tradingassistant.pipeline.event.TradeQualifiedEvent;
import com.tradingassistant.qualification.domain.TradeQualification;
import com.tradingassistant.risk.config.RiskEngineConfig;
import com.tradingassistant.risk.domain.RiskAssessment;
import com.tradingassistant.risk.domain.RiskContext;
import com.tradingassistant.risk.engine.RiskEngine;
import com.tradingassistant.risk.exception.RiskValidationException;
import com.tradingassistant.strategy.domain.StrategyEvaluation;

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
 * Assesses the risk boundaries of qualified trades when they are published.
 *
 * <p>The service consumes {@link TradeQualifiedEvent} and the assessment and
 * strategy events that provide its inputs. A qualification needs the strategy
 * evaluation and the market assessment of the same instant, so an event that
 * arrives before those are cached is staged in a bounded pending map and assessed
 * when they arrive. That makes the result independent of bus subscription order
 * and of the nested publication each earlier layer performs from its own handler.</p>
 *
 * <p>Only aligned data is used: an option assessment, candle or indicator of
 * another instant is ignored rather than reused. Every cache is bounded and
 * nothing is persisted.</p>
 */
public final class RiskAssessmentService implements MarketEventListener {

    private static final int MAX_CACHED_ENTRIES = 64;

    private final RiskEngine engine;
    private final RiskEngineConfig config;
    private final RiskMarketDataProvider provider;
    private final MarketEventPublisher publisher;
    private final Clock clock;
    private final Map<InstrumentKey, MarketAssessment> marketAssessments =
            boundedAssessments();
    private final Map<InstrumentKey, OptionsAssessment> optionsAssessments =
            boundedAssessments();
    private final Map<PendingKey, List<StrategyEvaluation>> strategyEvaluations = bounded();
    private final Map<PendingKey, List<TradeQualification>> pending = bounded();

    public RiskAssessmentService(RiskEngine engine,
                                 RiskEngineConfig config,
                                 RiskMarketDataProvider provider,
                                 MarketEventPublisher publisher,
                                 Clock clock) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void onEvent(MarketEvent event) {
        if (event == null) {
            throw new RiskValidationException("Event must not be null.");
        }
        if (!config.enabled()) {
            return;
        }
        if (event instanceof MarketAssessmentGeneratedEvent generated) {
            MarketAssessment assessment = generated.assessment();
            marketAssessments.put(assessment.instrument(), assessment);
            drain(new PendingKey(assessment.instrument(), assessment.timestamp(),
                    assessment.interval()));
        } else if (event instanceof OptionsAssessmentGeneratedEvent generated) {
            OptionsAssessment assessment = generated.assessment();
            optionsAssessments.put(assessment.spotInstrument(), assessment);
        } else if (event instanceof StrategyEvaluatedEvent generated) {
            cacheEvaluations(generated.evaluations());
        } else if (event instanceof TradeQualifiedEvent generated) {
            onQualifications(generated.qualifications());
        }
    }

    private void cacheEvaluations(List<StrategyEvaluation> evaluations) {
        Map<PendingKey, List<StrategyEvaluation>> batches = new LinkedHashMap<>();
        for (StrategyEvaluation evaluation : evaluations) {
            PendingKey key = new PendingKey(evaluation.instrument(), evaluation.timestamp(),
                    evaluation.interval());
            batches.computeIfAbsent(key, ignored -> new ArrayList<>()).add(evaluation);
        }
        batches.forEach((key, batch) -> {
            strategyEvaluations.put(key, List.copyOf(batch));
            drain(key);
        });
    }

    private void onQualifications(List<TradeQualification> qualifications) {
        Map<PendingKey, List<TradeQualification>> batches = new LinkedHashMap<>();
        for (TradeQualification qualification : qualifications) {
            PendingKey key = new PendingKey(qualification.instrument(),
                    qualification.timestamp(), qualification.interval());
            batches.computeIfAbsent(key, ignored -> new ArrayList<>()).add(qualification);
        }
        List<RiskAssessment> assessments = new ArrayList<>();
        batches.forEach((key, batch) -> assessments.addAll(assessOrStage(key, batch)));
        publish(assessments);
    }

    private void drain(PendingKey key) {
        List<TradeQualification> waiting = pending.remove(key);
        if (waiting == null || waiting.isEmpty()) {
            return;
        }
        publish(assessOrStage(key, List.copyOf(waiting)));
    }

    private List<RiskAssessment> assessOrStage(PendingKey key,
                                               List<TradeQualification> qualifications) {
        MarketAssessment market = marketAssessments.get(key.instrument());
        List<StrategyEvaluation> evaluations = strategyEvaluations.get(key);
        if (market == null || !market.timestamp().equals(key.timestamp())
                || market.interval() != key.interval() || evaluations == null) {
            pending.computeIfAbsent(key, ignored -> new ArrayList<>()).addAll(qualifications);
            return List.of();
        }
        List<RiskAssessment> assessments = new ArrayList<>(qualifications.size());
        for (TradeQualification qualification : qualifications) {
            Optional<StrategyEvaluation> evaluation = evaluations.stream()
                    .filter(candidate -> candidate.strategyId()
                            .equals(qualification.strategyId()))
                    .findFirst();
            if (evaluation.isEmpty()) {
                pending.computeIfAbsent(key, ignored -> new ArrayList<>()).add(qualification);
                continue;
            }
            assessments.add(engine.evaluate(context(evaluation.get(), qualification, market)));
        }
        return assessments;
    }

    private void publish(List<RiskAssessment> assessments) {
        if (assessments.isEmpty()) {
            return;
        }
        publisher.publish(new RiskAssessmentGeneratedEvent(List.copyOf(assessments),
                clock.instant()));
    }

    private RiskContext context(StrategyEvaluation evaluation, TradeQualification qualification,
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
        Map<String, IndicatorResult> indicators = new LinkedHashMap<>();
        provider.latestIndicators(instrument, interval).forEach((type, result) -> {
            if (result != null && result.timestamp().equals(evaluation.timestamp())) {
                indicators.put(type, result);
            }
        });
        return new RiskContext(evaluation, qualification, market,
                Optional.ofNullable(options), candle, Map.copyOf(indicators));
    }

    public int cachedMarketAssessments() {
        return marketAssessments.size();
    }

    public int cachedOptionsAssessments() {
        return optionsAssessments.size();
    }

    public int pendingQualifications() {
        return pending.values().stream().mapToInt(List::size).sum();
    }

    public void clearCaches() {
        marketAssessments.clear();
        optionsAssessments.clear();
        strategyEvaluations.clear();
        pending.clear();
    }

    private static <V> Map<InstrumentKey, V> boundedAssessments() {
        return Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<InstrumentKey, V> eldest) {
                return size() > MAX_CACHED_ENTRIES;
            }
        });
    }

    private static <V> Map<PendingKey, V> bounded() {
        return Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<PendingKey, V> eldest) {
                return size() > MAX_CACHED_ENTRIES;
            }
        });
    }

    /**
     * Identity of one staged batch: instrument, instant and interval.
     */
    record PendingKey(InstrumentKey instrument, Instant timestamp, Interval interval) {
    }
}
