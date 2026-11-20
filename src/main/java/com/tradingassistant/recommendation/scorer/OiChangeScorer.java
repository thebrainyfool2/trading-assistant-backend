package com.tradingassistant.recommendation.scorer;

import com.tradingassistant.recommendation.api.RecommendationScorer;
import com.tradingassistant.recommendation.domain.DirectionalBias;
import com.tradingassistant.recommendation.domain.RecommendationContext;
import com.tradingassistant.recommendation.domain.ScorerId;
import com.tradingassistant.recommendation.domain.ScorerResult;
import com.tradingassistant.recommendation.rule.ScorerSupport;

import java.util.Map;

/**
 * Scores the existing open-interest changes. Positive call change and positive
 * put change are read as opposing positioning; neither number is recalculated.
 */
public final class OiChangeScorer implements RecommendationScorer {

    @Override
    public ScorerId id() {
        return ScorerId.OI_CHANGE;
    }

    @Override
    public ScorerResult score(RecommendationContext context) {
        if (context.options().isEmpty()) {
            return ScorerSupport.result(id(), 0, DirectionalBias.UNKNOWN,
                    "No options assessment available for OI change.",
                    Map.of("state", "MISSING"));
        }
        var openInterest = context.options().get().openInterest();
        long callChange = 0;
        long putChange = 0;
        boolean observed = false;
        for (var strike : openInterest.strikes()) {
            if (strike.callOpenInterestChange().isPresent()) {
                callChange += strike.callOpenInterestChange().get();
                observed = true;
            }
            if (strike.putOpenInterestChange().isPresent()) {
                putChange += strike.putOpenInterestChange().get();
                observed = true;
            }
        }
        DirectionalBias bias = !observed || callChange == putChange
                ? DirectionalBias.NEUTRAL
                : callChange > putChange ? DirectionalBias.BULLISH
                : DirectionalBias.BEARISH;
        int base = !observed ? 0 : bias == DirectionalBias.NEUTRAL ? 60 : 100;
        int points = ScorerSupport.statusAdjusted(base, openInterest.status());
        return ScorerSupport.result(id(), points, bias,
                "Observed call OI change " + callChange + " and put OI change "
                        + putChange + ".",
                Map.of("callOpenInterestChange", Long.toString(callChange),
                        "putOpenInterestChange", Long.toString(putChange),
                        "observed", Boolean.toString(observed),
                        "status", openInterest.status().name()));
    }
}
