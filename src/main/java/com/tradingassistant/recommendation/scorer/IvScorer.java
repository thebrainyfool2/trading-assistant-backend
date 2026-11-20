package com.tradingassistant.recommendation.scorer;

import com.tradingassistant.recommendation.api.RecommendationScorer;
import com.tradingassistant.recommendation.domain.DirectionalBias;
import com.tradingassistant.recommendation.domain.RecommendationContext;
import com.tradingassistant.recommendation.domain.ScorerId;
import com.tradingassistant.recommendation.domain.ScorerResult;
import com.tradingassistant.recommendation.rule.ScorerSupport;

import java.util.Map;

/**
 * Scores the existing implied-volatility state. Stable or contracting premium is
 * treated as more usable for a long option than expanding premium.
 */
public final class IvScorer implements RecommendationScorer {

    @Override
    public ScorerId id() {
        return ScorerId.IV;
    }

    @Override
    public ScorerResult score(RecommendationContext context) {
        if (context.options().isEmpty()) {
            return ScorerSupport.result(id(), 0, DirectionalBias.UNKNOWN,
                    "No options assessment available for implied volatility.",
                    Map.of("state", "MISSING"));
        }
        var volatility = context.options().get().volatility();
        if (volatility.atmImpliedVolatility().isEmpty()) {
            return ScorerSupport.result(id(), 0, DirectionalBias.UNKNOWN,
                    "ATM implied volatility is unavailable.",
                    Map.of("status", volatility.status().name()));
        }
        int base = switch (volatility.state()) {
            case STABLE -> 100;
            case CONTRACTING -> 70;
            case EXPANDING -> 50;
            case UNKNOWN -> 0;
        };
        int points = ScorerSupport.statusAdjusted(base, volatility.status());
        return ScorerSupport.result(id(), points, DirectionalBias.NEUTRAL,
                volatility.summary() + " ATM IV "
                        + ScorerSupport.decimal(volatility.atmImpliedVolatility().get())
                        + ".",
                Map.of("state", volatility.state().name(),
                        "trend", volatility.trend().name(),
                        "status", volatility.status().name()));
    }
}
