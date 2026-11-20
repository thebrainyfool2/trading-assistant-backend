package com.tradingassistant.recommendation.scorer;

import com.tradingassistant.recommendation.api.RecommendationScorer;
import com.tradingassistant.recommendation.domain.DirectionalBias;
import com.tradingassistant.recommendation.domain.RecommendationContext;
import com.tradingassistant.recommendation.domain.ScorerId;
import com.tradingassistant.recommendation.domain.ScorerResult;
import com.tradingassistant.recommendation.rule.ScorerSupport;

import java.util.Map;

/**
 * Scores a momentum observation that is already classified by the market
 * assessment. Bullish and bearish momentum are both strong evidence for their
 * respective direction.
 */
public final class MomentumScorer implements RecommendationScorer {

    @Override
    public ScorerId id() {
        return ScorerId.MOMENTUM;
    }

    @Override
    public ScorerResult score(RecommendationContext context) {
        var momentum = context.market().momentum();
        var trendBias = ScorerSupport.trendBias(context.market());
        int base = switch (momentum.state()) {
            case STRENGTHENING, WEAKENING -> 100;
            case NEUTRAL -> 45;
            case UNKNOWN -> 0;
        };
        int points = ScorerSupport.statusAdjusted(base, momentum.status());
        DirectionalBias bias = switch (momentum.state()) {
            case STRENGTHENING -> trendBias == DirectionalBias.BEARISH
                    ? DirectionalBias.NEUTRAL : trendBias;
            case WEAKENING -> trendBias == DirectionalBias.BULLISH
                    ? DirectionalBias.NEUTRAL : trendBias;
            case NEUTRAL, UNKNOWN -> DirectionalBias.NEUTRAL;
        };
        return ScorerSupport.result(id(), points, bias,
                momentum.summary() + " Momentum inputs: "
                        + (momentum.indicators().isEmpty() ? "none"
                        : String.join(",", momentum.indicators())),
                Map.of("state", momentum.state().name(),
                        "status", momentum.status().name(),
                        "trendBias", trendBias.name()));
    }
}
