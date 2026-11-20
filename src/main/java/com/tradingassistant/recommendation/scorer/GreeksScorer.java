package com.tradingassistant.recommendation.scorer;

import com.tradingassistant.recommendation.api.RecommendationScorer;
import com.tradingassistant.recommendation.domain.DirectionalBias;
import com.tradingassistant.recommendation.domain.RecommendationContext;
import com.tradingassistant.recommendation.domain.ScorerId;
import com.tradingassistant.recommendation.domain.ScorerResult;
import com.tradingassistant.recommendation.rule.ScorerSupport;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Scores the direction of the existing net-delta exposure. Greeks are never
 * recalculated here.
 */
public final class GreeksScorer implements RecommendationScorer {

    @Override
    public ScorerId id() {
        return ScorerId.GREEKS;
    }

    @Override
    public ScorerResult score(RecommendationContext context) {
        if (context.options().isEmpty()) {
            return ScorerSupport.result(id(), 0, DirectionalBias.UNKNOWN,
                    "No options assessment available for Greeks.",
                    Map.of("state", "MISSING"));
        }
        var greeks = context.options().get().greeks();
        BigDecimal delta = greeks.netExposure().delta();
        DirectionalBias bias = delta.signum() > 0 ? DirectionalBias.BULLISH
                : delta.signum() < 0 ? DirectionalBias.BEARISH
                : DirectionalBias.NEUTRAL;
        int base = greeks.evaluatedLegs() == 0 ? 0
                : delta.signum() == 0 ? 60 : 100;
        int points = ScorerSupport.statusAdjusted(base, greeks.status());
        return ScorerSupport.result(id(), points, bias,
                greeks.summary() + " Net delta "
                        + ScorerSupport.decimal(delta) + " over "
                        + greeks.evaluatedLegs() + " evaluated legs.",
                Map.of("netDelta", ScorerSupport.decimal(delta),
                        "evaluatedLegs", Integer.toString(greeks.evaluatedLegs()),
                        "status", greeks.status().name()));
    }
}
