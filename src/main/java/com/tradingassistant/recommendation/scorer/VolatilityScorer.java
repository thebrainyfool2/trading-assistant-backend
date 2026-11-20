package com.tradingassistant.recommendation.scorer;

import com.tradingassistant.recommendation.api.RecommendationScorer;
import com.tradingassistant.recommendation.domain.DirectionalBias;
import com.tradingassistant.recommendation.domain.RecommendationContext;
import com.tradingassistant.recommendation.domain.ScorerId;
import com.tradingassistant.recommendation.domain.ScorerResult;
import com.tradingassistant.recommendation.rule.ScorerSupport;

import java.util.Map;

/**
 * Scores whether the volatility regime is usable for a directional option
 * purchase. It does not forecast volatility.
 */
public final class VolatilityScorer implements RecommendationScorer {

    @Override
    public ScorerId id() {
        return ScorerId.VOLATILITY;
    }

    @Override
    public ScorerResult score(RecommendationContext context) {
        var volatility = context.market().volatility();
        int base = switch (volatility.state()) {
            case STABLE -> 90;
            case EXPANDING -> 80;
            case CONTRACTING -> 55;
            case UNKNOWN -> 0;
        };
        int points = ScorerSupport.statusAdjusted(base, volatility.status());
        return ScorerSupport.result(id(), points, DirectionalBias.NEUTRAL,
                volatility.summary(),
                Map.of("state", volatility.state().name(),
                        "status", volatility.status().name()));
    }
}
