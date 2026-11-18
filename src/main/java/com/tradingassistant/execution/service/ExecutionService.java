package com.tradingassistant.execution.service;

import com.tradingassistant.execution.domain.ExecutionRequest;
import com.tradingassistant.execution.domain.ExecutionResult;
import com.tradingassistant.execution.domain.OrderIntent;
import com.tradingassistant.execution.domain.OrderRequest;
import com.tradingassistant.execution.engine.ExecutionEngine;
import com.tradingassistant.execution.exception.ExecutionValidationException;
import com.tradingassistant.execution.order.OrderRequestFactory;
import com.tradingassistant.execution.portfolio.PortfolioService;
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
import com.tradingassistant.qualification.domain.QualificationStatus;
import com.tradingassistant.qualification.domain.TradeQualification;
import com.tradingassistant.risk.domain.RiskAssessment;
import com.tradingassistant.strategy.domain.EvaluationStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/**
 * Pipeline-facing entry point of the execution layer.
 *
 * <p>The service listens to the last two stages of the pipeline, remembers the
 * immutable descriptions of the most recent instants in bounded caches, and turns a
 * caller's {@link OrderIntent} into an {@link ExecutionRequest} for the engine. It
 * never infers a direction: the intent supplies it.</p>
 *
 * <p>Risk assessments and market assessments are cached by identity and instant.
 * A qualification that is cached and not qualified blocks execution; a strategy
 * evaluation and an options assessment are retained for diagnostics only.</p>
 */
public final class ExecutionService implements MarketEventListener {

    public static final int CACHE_CAPACITY = 64;

    private final ExecutionEngine engine;
    private final ExecutionMarketDataProvider marketData;
    private final PortfolioService portfolio;
    private final Clock clock;
    private final BoundedCache<AssessmentKey, RiskAssessment> riskAssessments;
    private final BoundedCache<MarketKey, MarketAssessment> marketAssessments;
    private final BoundedCache<AssessmentKey, TradeQualification> qualifications;
    private final BoundedCache<AssessmentKey, EvaluationStatus> strategyEvaluations;
    private final BoundedCache<InstrumentKey, OptionsAssessment> optionsAssessments;

    public ExecutionService(ExecutionEngine engine,
                            ExecutionMarketDataProvider marketData,
                            Clock clock) {
        this(engine, marketData, engine.portfolio(), CACHE_CAPACITY, clock);
    }

    public ExecutionService(ExecutionEngine engine,
                            ExecutionMarketDataProvider marketData,
                            PortfolioService portfolio,
                            int cacheCapacity,
                            Clock clock) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        this.marketData = Objects.requireNonNull(marketData, "marketData must not be null");
        this.portfolio = Objects.requireNonNull(portfolio, "portfolio must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.riskAssessments = new BoundedCache<>(cacheCapacity);
        this.marketAssessments = new BoundedCache<>(cacheCapacity);
        this.qualifications = new BoundedCache<>(cacheCapacity);
        this.strategyEvaluations = new BoundedCache<>(cacheCapacity);
        this.optionsAssessments = new BoundedCache<>(cacheCapacity);
    }

    /**
     * Places an order for the most recent assessed state of a strategy and
     * instrument.
     *
     * @throws ExecutionValidationException when no usable assessment pair is cached
     */
    public ExecutionResult submit(OrderIntent intent) {
        Objects.requireNonNull(intent, "intent must not be null");
        RiskAssessment assessment = latestRisk(intent.strategyId(), intent.instrument())
                .orElseThrow(() -> new ExecutionValidationException(
                        "no risk assessment is cached for " + intent.strategyId() + " on "
                                + intent.instrument().symbolToken()));
        MarketAssessment market = marketAssessment(assessment.instrument(),
                assessment.interval(), assessment.timestamp())
                .orElseThrow(() -> new ExecutionValidationException(
                        "no market assessment is cached for the assessed instant"));
        rejectUnqualified(assessment);
        OrderRequest order = OrderRequestFactory.create(intent, assessment);
        return engine.execute(new ExecutionRequest(order, assessment, market));
    }

    /**
     * Direct pass-through used when a caller already holds the descriptions.
     */
    public ExecutionResult execute(ExecutionRequest request) {
        return engine.execute(request);
    }

    public ExecutionResult cancel(String orderId) {
        return engine.cancel(orderId);
    }

    @Override
    public void onEvent(MarketEvent event) {
        if (event == null) {
            return;
        }
        switch (event) {
            case RiskAssessmentGeneratedEvent risk -> risk.assessments()
                    .forEach(assessment -> riskAssessments.put(
                            key(assessment.strategyId(), assessment.instrument(),
                                    assessment.timestamp()), assessment));
            case MarketAssessmentGeneratedEvent market -> {
                MarketAssessment assessment = market.assessment();
                marketAssessments.put(new MarketKey(assessment.instrument(),
                        assessment.interval(), assessment.timestamp()), assessment);
                mark(assessment);
            }
            case StrategyEvaluatedEvent strategy -> strategy.evaluations().forEach(
                    evaluation -> strategyEvaluations.put(
                            key(evaluation.strategyId(), evaluation.instrument(),
                                    evaluation.timestamp()), evaluation.status()));
            case TradeQualifiedEvent qualification -> qualification.qualifications()
                    .forEach(qualified -> qualifications.put(
                            key(qualified.strategyId(), qualified.instrument(),
                                    qualified.timestamp()), qualified));
            case OptionsAssessmentGeneratedEvent options -> optionsAssessments.put(
                    options.assessment().spotInstrument(), options.assessment());
            default -> {
                // Earlier stages and execution's own output events need no caching.
            }
        }
    }

    /**
     * Most recent cached risk assessment for a strategy and instrument.
     */
    public Optional<RiskAssessment> latestRisk(String strategyId, InstrumentKey instrument) {
        if (strategyId == null || instrument == null) {
            return Optional.empty();
        }
        return riskAssessments.snapshot().stream()
                .filter(assessment -> assessment.strategyId().equals(strategyId)
                        && assessment.instrument().equals(instrument))
                .max(Comparator.comparing(RiskAssessment::timestamp));
    }

    public Optional<MarketAssessment> marketAssessment(InstrumentKey instrument,
                                                       Interval interval, Instant timestamp) {
        return marketAssessments.get(new MarketKey(instrument, interval, timestamp));
    }

    public Optional<OptionsAssessment> optionsAssessment(InstrumentKey instrument) {
        return optionsAssessments.get(instrument);
    }

    public Optional<EvaluationStatus> strategyStatus(String strategyId,
                                                     InstrumentKey instrument,
                                                     Instant timestamp) {
        return strategyEvaluations.get(key(strategyId, instrument, timestamp));
    }

    public int cachedRiskAssessmentCount() {
        return riskAssessments.size();
    }

    public int cachedMarketAssessmentCount() {
        return marketAssessments.size();
    }

    public int cachedQualificationCount() {
        return qualifications.size();
    }

    public int cachedOptionsAssessmentCount() {
        return optionsAssessments.size();
    }

    public int cacheCapacity() {
        return riskAssessments.capacity();
    }

    public PortfolioService portfolio() {
        return portfolio;
    }

    public ExecutionEngine engine() {
        return engine;
    }

    public Clock clock() {
        return clock;
    }

    @Override
    public String toString() {
        return "ExecutionService";
    }

    private void rejectUnqualified(RiskAssessment assessment) {
        qualifications.get(key(assessment.strategyId(), assessment.instrument(),
                assessment.timestamp())).ifPresent(qualification -> {
            if (qualification.status() != QualificationStatus.QUALIFIED) {
                throw new ExecutionValidationException(
                        "trade qualification is " + qualification.status());
            }
        });
    }

    private void mark(MarketAssessment assessment) {
        marketData.latestPrice(assessment.instrument(), assessment.interval())
                .ifPresent(price -> portfolio.applyMark(assessment.instrument(), price));
    }

    private static AssessmentKey key(String strategyId, InstrumentKey instrument,
                                     Instant timestamp) {
        return new AssessmentKey(strategyId, instrument, timestamp);
    }

    /**
     * Identity of a staged description.
     */
    public record AssessmentKey(String strategyId, InstrumentKey instrument,
                                Instant timestamp) {

        public AssessmentKey {
            Objects.requireNonNull(strategyId, "strategyId must not be null");
            Objects.requireNonNull(instrument, "instrument must not be null");
            Objects.requireNonNull(timestamp, "timestamp must not be null");
        }
    }

    /**
     * Identity of a market assessment.
     */
    public record MarketKey(InstrumentKey instrument, Interval interval, Instant timestamp) {

        public MarketKey {
            Objects.requireNonNull(instrument, "instrument must not be null");
            Objects.requireNonNull(interval, "interval must not be null");
            Objects.requireNonNull(timestamp, "timestamp must not be null");
        }
    }
}
