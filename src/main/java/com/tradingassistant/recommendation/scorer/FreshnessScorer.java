package com.tradingassistant.recommendation.scorer;

import com.tradingassistant.platform.readmodel.MarketDataFreshnessService.FreshnessStatus;
import com.tradingassistant.platform.readmodel.MarketDataFreshnessService.MarketSessionState;
import com.tradingassistant.recommendation.api.RecommendationScorer;
import com.tradingassistant.recommendation.domain.DirectionalBias;
import com.tradingassistant.recommendation.domain.RecommendationContext;
import com.tradingassistant.recommendation.domain.ScorerId;
import com.tradingassistant.recommendation.domain.ScorerResult;
import com.tradingassistant.recommendation.rule.ScorerSupport;

import java.util.Map;

/**
 * Scores the existing freshness observation and market-session state.
 */
public final class FreshnessScorer implements RecommendationScorer {

    @Override
    public ScorerId id() {
        return ScorerId.FRESHNESS;
    }

    @Override
    public ScorerResult score(RecommendationContext context) {
        var freshness = context.freshness();
        int base = switch (freshness.status()) {
            case FRESH -> 100;
            case STALE -> 0;
            case UNKNOWN -> 20;
        };
        if (freshness.status() == FreshnessStatus.FRESH
                && freshness.session() == MarketSessionState.CLOSED) {
            base -= 30;
        } else if (freshness.session() == MarketSessionState.UNKNOWN) {
            base -= 10;
        }
        return ScorerSupport.result(id(), ScorerSupport.clamp(base, 0, 100),
                DirectionalBias.NEUTRAL,
                "Freshness is " + freshness.status() + " and session is "
                        + freshness.session() + ".",
                Map.of("status", freshness.status().name(),
                        "session", freshness.session().name(),
                        "tickAgeSeconds", String.valueOf(freshness.tickAgeSeconds()),
                        "candleAgeSeconds",
                        String.valueOf(freshness.candleAgeSeconds())));
    }
}
