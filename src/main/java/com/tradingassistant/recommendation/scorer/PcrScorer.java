package com.tradingassistant.recommendation.scorer;

import com.tradingassistant.recommendation.api.RecommendationScorer;
import com.tradingassistant.recommendation.config.RecommendationConfig;
import com.tradingassistant.recommendation.domain.DirectionalBias;
import com.tradingassistant.recommendation.domain.RecommendationContext;
import com.tradingassistant.recommendation.domain.ScorerId;
import com.tradingassistant.recommendation.domain.ScorerResult;
import com.tradingassistant.recommendation.rule.ScorerSupport;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/**
 * Scores the existing chain put/call open-interest ratio against configured
 * thresholds.
 */
public final class PcrScorer implements RecommendationScorer {

    private final RecommendationConfig config;

    public PcrScorer(RecommendationConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    @Override
    public ScorerId id() {
        return ScorerId.PCR;
    }

    @Override
    public ScorerResult score(RecommendationContext context) {
        if (context.options().isEmpty()) {
            return ScorerSupport.result(id(), 0, DirectionalBias.UNKNOWN,
                    "No options assessment available for put/call ratio.",
                    Map.of("state", "MISSING"));
        }
        var pcr = context.options().get().putCallRatio();
        var ratio = pcr.chainOpenInterestRatio();
        if (ratio.isEmpty()) {
            return ScorerSupport.result(id(), 0, DirectionalBias.UNKNOWN,
                    "Put/call open-interest ratio is unavailable.", Map.of());
        }
        BigDecimal value = ratio.get();
        DirectionalBias bias = value.compareTo(config.pcrBullishThreshold()) >= 0
                ? DirectionalBias.BULLISH
                : value.compareTo(config.pcrBearishThreshold()) <= 0
                ? DirectionalBias.BEARISH : DirectionalBias.NEUTRAL;
        int base = bias == DirectionalBias.NEUTRAL ? 50 : 100;
        int points = ScorerSupport.statusAdjusted(base, pcr.status());
        return ScorerSupport.result(id(), points, bias,
                "Chain put/call open-interest ratio is " + ScorerSupport.decimal(value)
                        + "; configured bullish level is "
                        + ScorerSupport.decimal(config.pcrBullishThreshold())
                        + " and bearish level is "
                        + ScorerSupport.decimal(config.pcrBearishThreshold()) + ".",
                Map.of("ratio", ScorerSupport.decimal(value),
                        "status", pcr.status().name()));
    }
}
