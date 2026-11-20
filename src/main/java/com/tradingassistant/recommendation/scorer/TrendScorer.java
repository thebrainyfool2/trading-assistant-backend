package com.tradingassistant.recommendation.scorer;

import com.tradingassistant.recommendation.api.RecommendationScorer;
import com.tradingassistant.recommendation.domain.RecommendationContext;
import com.tradingassistant.recommendation.domain.ScorerId;
import com.tradingassistant.recommendation.domain.ScorerResult;
import com.tradingassistant.recommendation.rule.ScorerSupport;

import java.util.Map;

/**
 * Scores the strength and direction of the existing trend assessment.
 */
public final class TrendScorer implements RecommendationScorer {

    @Override
    public ScorerId id() {
        return ScorerId.TREND;
    }

    @Override
    public ScorerResult score(RecommendationContext context) {
        var trend = context.market().trend();
        int base = switch (trend.state()) {
            case BULLISH, BEARISH -> 100;
            case SIDEWAYS -> 40;
            case UNKNOWN -> 0;
        };
        int points = ScorerSupport.statusAdjusted(base, trend.status());
        String explanation = trend.summary() + " Trend inputs: "
                + (trend.indicators().isEmpty() ? "none"
                : String.join(",", trend.indicators()));
        return ScorerSupport.result(id(), points,
                ScorerSupport.trendBias(context.market()), explanation,
                Map.of("state", trend.state().name(),
                        "status", trend.status().name(),
                        "indicators", String.join(",", trend.indicators())));
    }
}
