package com.tradingassistant.platform.trade;

import com.tradingassistant.common.api.ErrorCode;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.platform.error.PlatformException;
import com.tradingassistant.recommendation.domain.Recommendation;
import com.tradingassistant.recommendation.domain.RecommendationContext;
import com.tradingassistant.recommendation.service.RecommendationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Read side for the current recommendation and its reference plan. It reads the
 * recommendation service's staged evidence and never recalculates a score.
 */
@Service
@ConditionalOnProperty(prefix = "app.broker.angel-one", name = "enabled",
        havingValue = "true")
public class RecommendationQueryService {

    private static final int MAX_LIMIT = 50;

    private final RecommendationService recommendations;
    private final TradePlanService plans;

    public RecommendationQueryService(RecommendationService recommendations,
                                      TradePlanService plans) {
        this.recommendations = recommendations;
        this.plans = plans;
    }

    public List<RecommendationDtos.RecommendationView> latest(int limit) {
        int bounded = Math.min(Math.max(limit, 1), MAX_LIMIT);
        return recommendations.latestAll().stream()
                .limit(bounded)
                .map(recommendation -> RecommendationDtos.RecommendationView.from(
                        recommendation, plan(recommendation)))
                .toList();
    }

    public RecommendationDtos.RecommendationView require(InstrumentKey instrument,
                                                         Interval interval) {
        Recommendation recommendation = recommendations.latest(instrument, interval)
                .orElseThrow(() -> new PlatformException(ErrorCode.RESOURCE_NOT_FOUND,
                        "No current recommendation exists for this instrument and interval."));
        return RecommendationDtos.RecommendationView.from(recommendation,
                plan(recommendation));
    }

    private Optional<TradePlan> plan(Recommendation recommendation) {
        Optional<RecommendationContext> context = recommendations.latestContext(
                recommendation.instrument(), recommendation.interval());
        return context.flatMap(value -> plans.plan(recommendation, value));
    }
}
