package com.tradingassistant.recommendation.api;

import com.tradingassistant.recommendation.domain.RecommendationContext;
import com.tradingassistant.recommendation.domain.ScorerId;
import com.tradingassistant.recommendation.domain.ScorerResult;

/**
 * One independent, deterministic scoring component.
 */
public interface RecommendationScorer {

    ScorerId id();

    ScorerResult score(RecommendationContext context);
}
