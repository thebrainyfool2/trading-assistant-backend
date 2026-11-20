package com.tradingassistant.recommendation.scorer;

import com.tradingassistant.recommendation.api.RecommendationScorer;
import com.tradingassistant.recommendation.domain.RecommendationContext;
import com.tradingassistant.recommendation.domain.ScorerId;
import com.tradingassistant.recommendation.domain.ScorerResult;
import com.tradingassistant.recommendation.rule.ScorerSupport;

import java.util.Map;

/**
 * Scores the existing participation classification, which describes whether
 * volume supports the observed move.
 */
public final class VolumeScorer implements RecommendationScorer {

    @Override
    public ScorerId id() {
        return ScorerId.VOLUME;
    }

    @Override
    public ScorerResult score(RecommendationContext context) {
        var participation = context.market().participation();
        int base = switch (participation.state()) {
            case STRONG -> 100;
            case AVERAGE -> 65;
            case WEAK -> 20;
            case UNKNOWN -> 0;
        };
        int points = ScorerSupport.statusAdjusted(base, participation.status());
        return ScorerSupport.result(id(), points,
                ScorerSupport.trendBias(context.market()),
                participation.summary(),
                Map.of("state", participation.state().name(),
                        "status", participation.status().name()));
    }
}
