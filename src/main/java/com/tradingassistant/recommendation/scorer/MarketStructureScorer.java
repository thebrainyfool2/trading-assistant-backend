package com.tradingassistant.recommendation.scorer;

import com.tradingassistant.recommendation.api.RecommendationScorer;
import com.tradingassistant.recommendation.domain.RecommendationContext;
import com.tradingassistant.recommendation.domain.ScorerId;
import com.tradingassistant.recommendation.domain.ScorerResult;
import com.tradingassistant.recommendation.rule.ScorerSupport;

import java.util.Map;

/**
 * Scores the existing swing-structure bias. Direction is read, not inferred.
 */
public final class MarketStructureScorer implements RecommendationScorer {

    @Override
    public ScorerId id() {
        return ScorerId.MARKET_STRUCTURE;
    }

    @Override
    public ScorerResult score(RecommendationContext context) {
        var structure = context.market().structure();
        int base = switch (structure.bias()) {
            case UPTREND, DOWNTREND -> 100;
            case RANGING -> 40;
            case UNKNOWN -> 0;
        };
        int points = ScorerSupport.statusAdjusted(base, structure.status());
        return ScorerSupport.result(id(), points,
                ScorerSupport.structureBias(context.market()),
                structure.summary(),
                Map.of("bias", structure.bias().name(),
                        "status", structure.status().name(),
                        "signals", structure.signals().toString()));
    }
}
