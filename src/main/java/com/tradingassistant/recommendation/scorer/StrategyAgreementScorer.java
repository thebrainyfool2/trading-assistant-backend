package com.tradingassistant.recommendation.scorer;

import com.tradingassistant.recommendation.api.RecommendationScorer;
import com.tradingassistant.recommendation.domain.DirectionalBias;
import com.tradingassistant.recommendation.domain.RecommendationContext;
import com.tradingassistant.recommendation.domain.ScorerId;
import com.tradingassistant.recommendation.domain.ScorerResult;
import com.tradingassistant.recommendation.rule.ScorerSupport;

import java.util.Map;

/**
 * Scores the share of existing strategy evaluations that passed. It does not
 * evaluate a strategy rule.
 */
public final class StrategyAgreementScorer implements RecommendationScorer {

    @Override
    public ScorerId id() {
        return ScorerId.STRATEGY_AGREEMENT;
    }

    @Override
    public ScorerResult score(RecommendationContext context) {
        if (context.strategies().isEmpty()) {
            return ScorerSupport.result(id(), 0, DirectionalBias.UNKNOWN,
                    "No strategy evaluations are available.",
                    Map.of("strategies", "0"));
        }
        long passed = context.strategies().stream()
                .filter(strategy -> strategy.status()
                        == com.tradingassistant.strategy.domain.EvaluationStatus.PASSED)
                .count();
        int total = context.strategies().size();
        int base = passed == total ? 100 : passed > 0 ? 60 : 20;
        return ScorerSupport.result(id(), base,
                ScorerSupport.trendBias(context.market()),
                "Passed strategy evaluations: " + passed + " of " + total + ".",
                Map.of("passed", Long.toString(passed),
                        "total", Integer.toString(total),
                        "strategyIds", context.strategies().stream()
                                .map(strategy -> strategy.strategyId())
                                .sorted().toList().toString()));
    }
}
