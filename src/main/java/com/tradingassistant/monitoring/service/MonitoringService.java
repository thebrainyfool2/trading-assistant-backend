package com.tradingassistant.monitoring.service;

import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.monitoring.config.MonitoringConfig;
import com.tradingassistant.monitoring.domain.ActiveTradeMonitoring;
import com.tradingassistant.monitoring.domain.ExitRecommendation;
import com.tradingassistant.monitoring.domain.ManualTrade;
import com.tradingassistant.monitoring.domain.MonitoringAlert;
import com.tradingassistant.monitoring.domain.MonitoringAlertType;
import com.tradingassistant.monitoring.domain.MonitoringHistoryEntry;
import com.tradingassistant.monitoring.domain.MonitoringWarning;
import com.tradingassistant.monitoring.domain.TradeMonitoringContext;
import com.tradingassistant.monitoring.domain.TradeMonitoringResult;
import com.tradingassistant.monitoring.engine.TradeMonitoringEngine;
import com.tradingassistant.monitoring.event.ExitRecommendedEvent;
import com.tradingassistant.monitoring.event.MonitoringEventBus;
import com.tradingassistant.monitoring.event.MonitoringStartedEvent;
import com.tradingassistant.monitoring.event.MonitoringStateChangedEvent;
import com.tradingassistant.monitoring.event.MonitoringStoppedEvent;
import com.tradingassistant.monitoring.event.MonitoringUpdatedEvent;
import com.tradingassistant.monitoring.event.MonitoringWarningEvent;
import com.tradingassistant.monitoring.exception.MonitoringValidationException;
import com.tradingassistant.pipeline.event.CandleCompletedEvent;
import com.tradingassistant.pipeline.event.MarketEvent;
import com.tradingassistant.pipeline.event.MarketEventListener;
import com.tradingassistant.pipeline.event.MarketAssessmentGeneratedEvent;
import com.tradingassistant.pipeline.event.OptionsAssessmentGeneratedEvent;
import com.tradingassistant.pipeline.event.RiskAssessmentGeneratedEvent;
import com.tradingassistant.pipeline.event.StrategyEvaluatedEvent;
import com.tradingassistant.pipeline.event.TradeQualifiedEvent;
import com.tradingassistant.qualification.domain.TradeQualification;
import com.tradingassistant.recommendation.domain.Recommendation;
import com.tradingassistant.recommendation.domain.RecommendationContext;
import com.tradingassistant.recommendation.event.RecommendationEvent;
import com.tradingassistant.recommendation.event.RecommendationGeneratedEvent;
import com.tradingassistant.recommendation.event.RecommendationUpdatedEvent;
import com.tradingassistant.recommendation.service.RecommendationService;
import com.tradingassistant.risk.domain.RiskAssessment;
import com.tradingassistant.strategy.domain.StrategyEvaluation;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live orchestration around the pure monitoring engine.
 *
 * <p>Every completed candle and every recommendation update rescans active
 * trades. It reuses the recommendation service's staged outputs and never
 * creates a second recommendation or recalculates indicators. Persistence and
 * events happen only when the observable monitoring result changes.</p>
 */
public final class MonitoringService implements MarketEventListener {

    private final RecommendationService recommendations;
    private final TradeMonitoringEngine engine;
    private final MonitoringStore store;
    private final MonitoringEventBus events;
    private final MonitoringConfig config;
    private final Clock clock;
    private final Map<String, String> lastProblem = new ConcurrentHashMap<>();

    public MonitoringService(RecommendationService recommendations,
                             TradeMonitoringEngine engine,
                             MonitoringStore store,
                             MonitoringEventBus events,
                             MonitoringConfig config,
                             Clock clock) {
        this.recommendations = Objects.requireNonNull(recommendations,
                "recommendations must not be null");
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.events = Objects.requireNonNull(events, "events must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public void register(ManualTrade trade) {
        Objects.requireNonNull(trade, "trade must not be null");
        if (!trade.active()) {
            throw new MonitoringValidationException("only ACTIVE trades can be registered");
        }
        if (store.trade(trade.tradeId()).isPresent()) {
            throw new MonitoringValidationException(
                    "a trade with this id already exists: " + trade.tradeId());
        }
        if (recommendations.latest(trade.instrument(), trade.interval()).isEmpty()) {
            throw new MonitoringValidationException(
                    "no recommendation is available for the trade");
        }
        store.saveTrade(trade);
        monitor(trade);
    }

    public void close(String tradeId, String reason) {
        ManualTrade trade = store.trade(tradeId)
                .orElseThrow(() -> new MonitoringValidationException(
                        "trade not found: " + tradeId));
        if (!trade.active()) {
            return;
        }
        store.saveTrade(trade.closed());
        events.publish(new MonitoringStoppedEvent(trade.tradeId(), reason,
                clock.instant()));
        recordAlert(new MonitoringAlert(trade.tradeId(), clock.instant(),
                MonitoringAlertType.MONITORING_STOPPED,
                "monitoring stopped: " + (reason == null ? "trade closed" : reason),
                Map.of()));
    }

    public void onRecommendationEvent(RecommendationEvent event) {
        if (!config.enabled() || event == null) {
            return;
        }
        if (event instanceof RecommendationGeneratedEvent generated) {
            rescan(generated.recommendation());
        } else if (event instanceof RecommendationUpdatedEvent updated) {
            rescan(updated.recommendation());
        }
    }

    @Override
    public void onEvent(MarketEvent event) {
        if (!config.enabled() || event == null) {
            return;
        }
        if (event instanceof CandleCompletedEvent candle) {
            rescan(candle.candle().instrument(), candle.candle().interval());
        } else if (event instanceof MarketAssessmentGeneratedEvent generated) {
            rescan(generated.assessment().instrument(), generated.assessment().interval());
        } else if (event instanceof OptionsAssessmentGeneratedEvent generated) {
            rescan(generated.assessment().spotInstrument());
        } else if (event instanceof StrategyEvaluatedEvent generated) {
            generated.evaluations().forEach(value ->
                    rescan(value.instrument(), value.interval()));
        } else if (event instanceof TradeQualifiedEvent generated) {
            generated.qualifications().forEach(value ->
                    rescan(value.instrument(), value.interval()));
        } else if (event instanceof RiskAssessmentGeneratedEvent generated) {
            generated.assessments().forEach(value ->
                    rescan(value.instrument(), value.interval()));
        }
    }

    public void rescan(InstrumentKey instrument, Interval interval) {
        for (ManualTrade trade : store.activeTrades()) {
            if (trade.matches(instrument, interval)) {
                monitor(trade);
            }
        }
    }

    private void rescan(InstrumentKey instrument) {
        for (ManualTrade trade : store.activeTrades()) {
            if (trade.instrument().equals(instrument)) {
                monitor(trade);
            }
        }
    }

    public List<ActiveTradeMonitoring> active() {
        return store.activeTrades().stream()
                .map(trade -> new ActiveTradeMonitoring(trade,
                        store.latestResult(trade.tradeId())))
                .toList();
    }

    public List<MonitoringHistoryEntry> history(String tradeId, int limit) {
        if (tradeId == null || tradeId.isBlank()) {
            return store.history(limit);
        }
        return store.history(tradeId.trim(), limit);
    }

    public List<MonitoringAlert> alerts(int limit) {
        return store.alerts(limit);
    }

    private void rescan(Recommendation recommendation) {
        rescan(recommendation.instrument(), recommendation.interval());
    }

    private void monitor(ManualTrade trade) {
        Optional<Recommendation> latest = recommendations.latest(trade.instrument(),
                trade.interval());
        if (latest.isEmpty()) {
            return;
        }
        Optional<RecommendationContext> source = recommendations.latestContext(
                trade.instrument(), trade.interval());
        if (source.isEmpty()) {
            return;
        }
        Optional<TradeMonitoringContext> context = context(trade, latest.get(),
                source.get(), store.latestResult(trade.tradeId()));
        if (context.isEmpty()) {
            return;
        }
        TradeMonitoringResult result;
        try {
            result = engine.evaluate(context.get());
        } catch (MonitoringValidationException rejected) {
            noteProblem(trade, rejected.getMessage());
            return;
        }
        lastProblem.remove(trade.tradeId());
        Optional<TradeMonitoringResult> previous = store.latestResult(trade.tradeId());
        if (previous.isPresent() && sameObservation(previous.get(), result)) {
            return;
        }
        store.saveResult(result);
        store.appendHistory(history(result, previous));
        publish(result, previous);
    }

    private Optional<TradeMonitoringContext> context(ManualTrade trade,
                                                     Recommendation recommendation,
                                                     RecommendationContext source,
                                                     Optional<TradeMonitoringResult> previous) {
        if (source.options().isEmpty() || source.strategies().isEmpty()
                || source.qualifications().isEmpty() || source.risks().isEmpty()) {
            noteProblem(trade, "monitoring context is missing options, strategy, "
                    + "qualification or risk evidence");
            return Optional.empty();
        }
        TradeQualification qualification = source.qualifications().stream()
                .filter(TradeQualification::qualified)
                .findFirst()
                .orElse(source.qualifications().getFirst());
        StrategyEvaluation strategy = source.strategies().stream()
                .filter(value -> value.strategyId().equals(qualification.strategyId()))
                .findFirst()
                .orElse(source.strategies().getFirst());
        RiskAssessment risk = source.risks().stream()
                .filter(value -> value.strategyId().equals(qualification.strategyId())
                        && value.withinLimits())
                .findFirst()
                .orElseGet(() -> source.risks().stream()
                        .filter(value -> value.strategyId()
                                .equals(qualification.strategyId()))
                        .findFirst()
                        .orElse(source.risks().getFirst()));
        return Optional.of(new TradeMonitoringContext(trade, recommendation,
                source.market(), source.indicators(), source.options().get(), strategy,
                qualification, risk, source.freshness(), previous));
    }

    private void publish(TradeMonitoringResult result,
                         Optional<TradeMonitoringResult> previous) {
        if (previous.isEmpty()) {
            events.publish(new MonitoringStartedEvent(result, clock.instant()));
            if (result.exitRecommendation() != ExitRecommendation.HOLD) {
                events.publish(new ExitRecommendedEvent(result,
                        ExitRecommendation.HOLD, result.exitRecommendation(),
                        clock.instant()));
                recordAlert(new MonitoringAlert(result.tradeId(), result.timestamp(),
                        MonitoringAlertType.EXIT_RECOMMENDATION_CHANGED,
                        "initial exit recommendation is "
                                + result.exitRecommendation().name(), Map.of()));
            }
        } else {
            events.publish(new MonitoringUpdatedEvent(result, clock.instant()));
            if (previous.get().monitoringState() != result.monitoringState()) {
                events.publish(new MonitoringStateChangedEvent(result,
                        previous.get().monitoringState(), result.monitoringState(),
                        clock.instant()));
                recordAlert(new MonitoringAlert(result.tradeId(), result.timestamp(),
                        MonitoringAlertType.STATE_CHANGED,
                        "monitoring state changed from "
                                + previous.get().monitoringState().name() + " to "
                                + result.monitoringState().name(), Map.of()));
            }
            if (previous.get().exitRecommendation() != result.exitRecommendation()) {
                events.publish(new ExitRecommendedEvent(result,
                        previous.get().exitRecommendation(),
                        result.exitRecommendation(), clock.instant()));
                recordAlert(new MonitoringAlert(result.tradeId(), result.timestamp(),
                        MonitoringAlertType.EXIT_RECOMMENDATION_CHANGED,
                        "exit recommendation changed from "
                                + previous.get().exitRecommendation().name() + " to "
                                + result.exitRecommendation().name(), Map.of()));
            }
        }
        for (MonitoringWarning warning : result.warnings()) {
            recordAlert(new MonitoringAlert(result.tradeId(), result.timestamp(),
                    warning.type(), warning.message(), warning.diagnostics()));
        }
    }

    private void noteProblem(ManualTrade trade, String message) {
        String text = message == null || message.isBlank()
                ? "monitoring context was rejected" : message;
        String previous = lastProblem.put(trade.tradeId(), text);
        if (text.equals(previous)) {
            return;
        }
        MonitoringAlertType type = text.toLowerCase(java.util.Locale.ROOT).contains("fresh")
                ? MonitoringAlertType.STALE_DATA
                : MonitoringAlertType.INVALID_CONTEXT;
        recordAlert(new MonitoringAlert(trade.tradeId(), clock.instant(), type, text,
                Map.of()));
    }

    private void recordAlert(MonitoringAlert alert) {
        store.appendAlert(alert);
        events.publish(new MonitoringWarningEvent(alert.tradeId(), alert.type(),
                alert.message(), alert.diagnostics(), clock.instant()));
    }

    private static MonitoringHistoryEntry history(TradeMonitoringResult result,
                                                  Optional<TradeMonitoringResult> previous) {
        return new MonitoringHistoryEntry(result.tradeId(), result.timestamp(),
                previous.map(TradeMonitoringResult::monitoringState),
                result.monitoringState(),
                previous.map(TradeMonitoringResult::exitRecommendation),
                result.exitRecommendation(),
                previous.map(TradeMonitoringResult::currentConfidence)
                        .orElse(result.currentConfidence()),
                result.currentConfidence(),
                previous.map(TradeMonitoringResult::currentDirectionalEdge)
                        .orElse(result.currentDirectionalEdge()),
                result.currentDirectionalEdge(), result.explanation(), result.warnings());
    }

    private static boolean sameObservation(TradeMonitoringResult previous,
                                           TradeMonitoringResult current) {
        return previous.monitoringState() == current.monitoringState()
                && previous.exitRecommendation() == current.exitRecommendation()
                && previous.currentConfidence().compareTo(current.currentConfidence()) == 0
                && previous.currentDirectionalEdge()
                .compareTo(current.currentDirectionalEdge()) == 0
                && previous.componentScores().equals(current.componentScores())
                && previous.warnings().equals(current.warnings());
    }
}