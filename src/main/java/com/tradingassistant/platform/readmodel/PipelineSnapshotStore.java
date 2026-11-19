package com.tradingassistant.platform.readmodel;

import com.tradingassistant.intelligence.domain.MarketAssessment;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.options.domain.OptionsAssessment;
import com.tradingassistant.pipeline.event.MarketAssessmentGeneratedEvent;
import com.tradingassistant.pipeline.event.MarketEvent;
import com.tradingassistant.pipeline.event.MarketEventListener;
import com.tradingassistant.pipeline.event.OptionsAssessmentGeneratedEvent;
import com.tradingassistant.pipeline.event.RiskAssessmentGeneratedEvent;
import com.tradingassistant.pipeline.event.StrategyEvaluatedEvent;
import com.tradingassistant.pipeline.event.TradeQualifiedEvent;
import com.tradingassistant.qualification.domain.TradeQualification;
import com.tradingassistant.risk.domain.RiskAssessment;
import com.tradingassistant.strategy.domain.StrategyEvaluation;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Bounded read-side projection of the latest engine descriptions.
 *
 * <p>The engine services own their caches and expose no query API; the platform
 * therefore observes the events those services publish. Nothing here mutates the
 * engine. The store is in memory and bounded: it is a view for clients, not a
 * system of record, and it is rebuilt from the pipeline after a restart.</p>
 */
@Component
public class PipelineSnapshotStore implements MarketEventListener {

    public static final int CAPACITY = 128;

    private final Map<MarketKey, MarketAssessment> marketAssessments =
            new ConcurrentHashMap<>();
    private final Map<InstrumentKey, OptionsAssessment> optionsAssessments =
            new ConcurrentHashMap<>();
    private final Map<StrategyKey, StrategyEvaluation> strategyEvaluations =
            new ConcurrentHashMap<>();
    private final Map<StrategyKey, RiskAssessment> riskAssessments =
            new ConcurrentHashMap<>();
    private final Map<StrategyKey, TradeQualification> qualifications =
            new ConcurrentHashMap<>();

    @Override
    public void onEvent(MarketEvent event) {
        if (event == null) {
            return;
        }
        switch (event) {
            case MarketAssessmentGeneratedEvent generated -> {
                MarketAssessment assessment = generated.assessment();
                putBounded(marketAssessments,
                        new MarketKey(assessment.instrument(), assessment.interval()),
                        assessment, MarketAssessment::timestamp);
            }
            case OptionsAssessmentGeneratedEvent generated ->
                    putBounded(optionsAssessments,
                            generated.assessment().spotInstrument(),
                            generated.assessment(), OptionsAssessment::timestamp);
            case StrategyEvaluatedEvent generated ->
                    generated.evaluations().forEach(evaluation ->
                            putBounded(strategyEvaluations,
                                    new StrategyKey(evaluation.strategyId(),
                                            evaluation.instrument(), evaluation.interval()),
                                    evaluation, StrategyEvaluation::timestamp));
            case RiskAssessmentGeneratedEvent generated ->
                    generated.assessments().forEach(assessment ->
                            putBounded(riskAssessments,
                                    new StrategyKey(assessment.strategyId(),
                                            assessment.instrument(), assessment.interval()),
                                    assessment, RiskAssessment::timestamp));
            case TradeQualifiedEvent generated ->
                    generated.qualifications().forEach(qualification ->
                            putBounded(qualifications,
                                    new StrategyKey(qualification.strategyId(),
                                            qualification.instrument(),
                                            qualification.interval()),
                                    qualification, TradeQualification::timestamp));
            default -> {
                // Only the description stages are projected here.
            }
        }
    }

    public List<MarketAssessment> marketAssessments(int limit) {
        return latest(marketAssessments.values(), MarketAssessment::timestamp, limit);
    }

    public Optional<MarketAssessment> marketAssessment(InstrumentKey instrument,
                                                       Interval interval) {
        return Optional.ofNullable(marketAssessments.get(new MarketKey(instrument,
                interval)));
    }

    public List<OptionsAssessment> optionsAssessments(int limit) {
        return latest(optionsAssessments.values(), OptionsAssessment::timestamp, limit);
    }

    public Optional<OptionsAssessment> optionsAssessment(InstrumentKey instrument) {
        return Optional.ofNullable(optionsAssessments.get(instrument));
    }

    public List<StrategyEvaluation> strategyEvaluations(int limit) {
        return latest(strategyEvaluations.values(), StrategyEvaluation::timestamp, limit);
    }

    public List<RiskAssessment> riskAssessments(int limit) {
        return latest(riskAssessments.values(), RiskAssessment::timestamp, limit);
    }

    public List<TradeQualification> qualifications(int limit) {
        return latest(qualifications.values(), TradeQualification::timestamp, limit);
    }

    public int size() {
        return marketAssessments.size() + optionsAssessments.size()
                + strategyEvaluations.size() + riskAssessments.size()
                + qualifications.size();
    }

    public void clear() {
        marketAssessments.clear();
        optionsAssessments.clear();
        strategyEvaluations.clear();
        riskAssessments.clear();
        qualifications.clear();
    }

    /**
     * Removes descriptions older than the supplied instant.
     */
    public int trim(Instant olderThan) {
        if (olderThan == null) {
            return 0;
        }
        int removed = trimMap(marketAssessments, MarketAssessment::timestamp, olderThan);
        removed += trimMap(optionsAssessments, OptionsAssessment::timestamp, olderThan);
        removed += trimMap(strategyEvaluations, StrategyEvaluation::timestamp, olderThan);
        removed += trimMap(riskAssessments, RiskAssessment::timestamp, olderThan);
        removed += trimMap(qualifications, TradeQualification::timestamp, olderThan);
        return removed;
    }

    private static <K, V> void putBounded(Map<K, V> map, K key, V value,
                                          Function<V, Instant> timestamp) {
        Objects.requireNonNull(value, "value must not be null");
        map.put(key, value);
        if (map.size() <= CAPACITY) {
            return;
        }
        K oldestKey = null;
        Instant oldest = null;
        for (Map.Entry<K, V> entry : map.entrySet()) {
            Instant candidate = timestamp.apply(entry.getValue());
            if (oldest == null || candidate.isBefore(oldest)) {
                oldest = candidate;
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey != null) {
            map.remove(oldestKey);
        }
    }

    private static <V> List<V> latest(Iterable<V> values, Function<V, Instant> timestamp,
                                      int limit) {
        int bounded = Math.min(Math.max(limit, 1), 100);
        return java.util.stream.StreamSupport.stream(values.spliterator(), false)
                .sorted(Comparator.comparing(timestamp).reversed())
                .limit(bounded)
                .toList();
    }

    private static <K, V> int trimMap(Map<K, V> map, Function<V, Instant> timestamp,
                                      Instant olderThan) {
        int removed = 0;
        for (Map.Entry<K, V> entry : map.entrySet()) {
            if (timestamp.apply(entry.getValue()).isBefore(olderThan)
                    && map.remove(entry.getKey(), entry.getValue())) {
                removed++;
            }
        }
        return removed;
    }

    /**
     * Identity of a market description.
     */
    public record MarketKey(InstrumentKey instrument, Interval interval) {

        public MarketKey {
            instrument = Objects.requireNonNull(instrument, "instrument must not be null");
            interval = Objects.requireNonNull(interval, "interval must not be null");
        }
    }

    /**
     * Identity of a strategy-scoped description.
     */
    public record StrategyKey(String strategyId, InstrumentKey instrument,
                              Interval interval) {

        public StrategyKey {
            strategyId = Objects.requireNonNull(strategyId, "strategyId must not be null");
            instrument = Objects.requireNonNull(instrument, "instrument must not be null");
            interval = Objects.requireNonNull(interval, "interval must not be null");
        }
    }
}
