package com.tradingassistant.recommendation.scorer;

import com.tradingassistant.recommendation.api.RecommendationScorer;
import com.tradingassistant.recommendation.domain.DirectionalBias;
import com.tradingassistant.recommendation.domain.RecommendationContext;
import com.tradingassistant.recommendation.domain.ScorerId;
import com.tradingassistant.recommendation.domain.ScorerResult;
import com.tradingassistant.recommendation.rule.ScorerSupport;

import java.util.Map;

/**
 * Scores the existing exchange-session classification. A closed or unresolved
 * session cannot support a fresh option entry.
 */
public final class MarketSessionScorer implements RecommendationScorer {

    @Override
    public ScorerId id() {
        return ScorerId.MARKET_SESSION;
    }

    @Override
    public ScorerResult score(RecommendationContext context) {
        var session = context.market().session();
        int base = switch (session.phase()) {
            case OPENING, REGULAR, CLOSING -> 100;
            case PRE_OPEN -> 50;
            case AFTER_MARKET -> 0;
            case UNKNOWN -> 0;
        };
        int points = ScorerSupport.statusAdjusted(base, session.status());
        return ScorerSupport.result(id(), points, DirectionalBias.NEUTRAL,
                session.summary(),
                Map.of("phase", session.phase().name(),
                        "status", session.status().name(),
                        "withinSession", Boolean.toString(session.withinSession())));
    }
}
