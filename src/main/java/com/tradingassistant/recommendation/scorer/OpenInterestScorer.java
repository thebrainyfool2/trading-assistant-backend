package com.tradingassistant.recommendation.scorer;

import com.tradingassistant.options.domain.OpenInterestBuildUp;
import com.tradingassistant.recommendation.api.RecommendationScorer;
import com.tradingassistant.recommendation.domain.DirectionalBias;
import com.tradingassistant.recommendation.domain.RecommendationContext;
import com.tradingassistant.recommendation.domain.ScorerId;
import com.tradingassistant.recommendation.domain.ScorerResult;
import com.tradingassistant.recommendation.rule.ScorerSupport;

import java.util.Map;

/**
 * Scores the directional positioning already classified in the chain's open
 * interest summary. It never reclassifies a build-up.
 */
public final class OpenInterestScorer implements RecommendationScorer {

    @Override
    public ScorerId id() {
        return ScorerId.OPEN_INTEREST;
    }

    @Override
    public ScorerResult score(RecommendationContext context) {
        if (context.options().isEmpty()) {
            return ScorerSupport.result(id(), 0, DirectionalBias.UNKNOWN,
                    "No options assessment available for open interest.",
                    Map.of("state", "MISSING"));
        }
        var openInterest = context.options().get().openInterest();
        int bullish = 0;
        int bearish = 0;
        for (var strike : openInterest.strikes()) {
            if (strike.callBuildUp() == OpenInterestBuildUp.LONG_BUILD_UP
                    || strike.callBuildUp() == OpenInterestBuildUp.SHORT_COVERING) {
                bullish++;
            }
            if (strike.callBuildUp() == OpenInterestBuildUp.LONG_UNWINDING
                    || strike.callBuildUp() == OpenInterestBuildUp.SHORT_BUILD_UP) {
                bearish++;
            }
            if (strike.putBuildUp() == OpenInterestBuildUp.LONG_BUILD_UP
                    || strike.putBuildUp() == OpenInterestBuildUp.SHORT_COVERING) {
                bearish++;
            }
            if (strike.putBuildUp() == OpenInterestBuildUp.LONG_UNWINDING
                    || strike.putBuildUp() == OpenInterestBuildUp.SHORT_BUILD_UP) {
                bullish++;
            }
        }
        DirectionalBias bias = bullish > bearish ? DirectionalBias.BULLISH
                : bearish > bullish ? DirectionalBias.BEARISH
                : DirectionalBias.NEUTRAL;
        int base = openInterest.totalOpenInterest() == 0 ? 0
                : bullish == bearish ? 60 : 100;
        int points = ScorerSupport.statusAdjusted(base, openInterest.status());
        return ScorerSupport.result(id(), points, bias,
                openInterest.summary() + " Bullish build-ups: " + bullish
                        + ", bearish build-ups: " + bearish + ".",
                Map.of("bullishBuildUps", Integer.toString(bullish),
                        "bearishBuildUps", Integer.toString(bearish),
                        "totalOpenInterest", Long.toString(openInterest.totalOpenInterest()),
                        "status", openInterest.status().name()));
    }
}
