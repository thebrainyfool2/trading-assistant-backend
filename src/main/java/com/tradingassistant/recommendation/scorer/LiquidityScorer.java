package com.tradingassistant.recommendation.scorer;

import com.tradingassistant.recommendation.api.RecommendationScorer;
import com.tradingassistant.recommendation.domain.DirectionalBias;
import com.tradingassistant.recommendation.domain.RecommendationContext;
import com.tradingassistant.recommendation.domain.ScorerId;
import com.tradingassistant.recommendation.domain.ScorerResult;
import com.tradingassistant.recommendation.rule.ScorerSupport;

import java.util.Map;

/**
 * Scores the option-market liquidity assessment. Missing option analytics are
 * evidence of unknown tradability, not a reason to invent a quote.
 */
public final class LiquidityScorer implements RecommendationScorer {

    @Override
    public ScorerId id() {
        return ScorerId.LIQUIDITY;
    }

    @Override
    public ScorerResult score(RecommendationContext context) {
        if (context.options().isEmpty()) {
            return ScorerSupport.result(id(), 0, DirectionalBias.UNKNOWN,
                    "No options assessment available for liquidity.",
                    Map.of("state", "MISSING"));
        }
        var liquidity = context.options().get().liquidity();
        int base = switch (liquidity.state()) {
            case LIQUID -> 100;
            case MODERATE -> 70;
            case ILLIQUID -> 20;
            case UNKNOWN -> 0;
        };
        int points = ScorerSupport.statusAdjusted(base, liquidity.status());
        if (liquidity.wideSpreadLegs() > 0) {
            points = ScorerSupport.clamp(points - 10, 0, 100);
        }
        if (liquidity.thinLegs() > 0) {
            points = ScorerSupport.clamp(points - 10, 0, 100);
        }
        return ScorerSupport.result(id(), points, DirectionalBias.NEUTRAL,
                liquidity.summary(),
                Map.of("state", liquidity.state().name(),
                        "status", liquidity.status().name(),
                        "assessedLegs", Integer.toString(liquidity.assessedLegs()),
                        "wideSpreadLegs", Integer.toString(liquidity.wideSpreadLegs()),
                        "thinLegs", Integer.toString(liquidity.thinLegs())));
    }
}
