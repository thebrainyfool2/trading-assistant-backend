package com.tradingassistant.platform.readmodel;

import com.tradingassistant.intelligence.domain.MarketAssessment;
import com.tradingassistant.options.domain.OptionsAssessment;
import com.tradingassistant.qualification.domain.TradeQualification;
import com.tradingassistant.risk.domain.RiskAssessment;
import com.tradingassistant.strategy.domain.StrategyDefinition;
import com.tradingassistant.strategy.domain.StrategyEvaluation;
import com.tradingassistant.strategy.registry.StrategyRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Read use cases over the engine's latest published descriptions.
 *
 * <p>Returns immutable engine types unchanged; the web layer projects them into
 * API views. No assessment is recomputed here.</p>
 */
@Service
public class AnalysisQueryService {

    private static final int DEFAULT_LIMIT = 25;

    private final PipelineSnapshotStore store;
    private final ObjectProvider<StrategyRegistry> strategyRegistry;
    private final Clock clock;

    public AnalysisQueryService(PipelineSnapshotStore store,
                                ObjectProvider<StrategyRegistry> strategyRegistry,
                                Clock clock) {
        this.store = store;
        this.strategyRegistry = strategyRegistry;
        this.clock = clock;
    }

    public List<MarketAssessment> markets(Integer limit) {
        return store.marketAssessments(normalise(limit));
    }

    public List<OptionsAssessment> options(Integer limit) {
        return store.optionsAssessments(normalise(limit));
    }

    public List<StrategyEvaluation> strategies(Integer limit) {
        return store.strategyEvaluations(normalise(limit));
    }

    public List<RiskAssessment> risks(Integer limit) {
        return store.riskAssessments(normalise(limit));
    }

    public List<TradeQualification> qualifications(Integer limit) {
        return store.qualifications(normalise(limit));
    }

    /**
     * Registry definitions are available only when the broker-backed pipeline is
     * running; an empty list is returned otherwise.
     */
    public List<StrategyDefinition> definitions() {
        StrategyRegistry registry = strategyRegistry.getIfAvailable();
        return registry == null ? List.of() : registry.snapshot();
    }

    public boolean engineAvailable() {
        StrategyRegistry registry = strategyRegistry.getIfAvailable();
        return registry != null;
    }

    public int readModelEntries() {
        return store.size();
    }

    public Instant now() {
        return clock.instant();
    }

    private static int normalise(Integer limit) {
        return limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), 100);
    }
}
