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
 * Scores the existing IV rank against configured inexpensive/expensive bands.
 */
public final class IvRankScorer implements RecommendationScorer {

    private final RecommendationConfig config;

    public IvRankScorer(RecommendationConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    @Override
    public ScorerId id() {
        return ScorerId.IV_RANK;
    }

    @Override
    public ScorerResult score(RecommendationContext context) {
        if (context.options().isEmpty()) {
            return ScorerSupport.result(id(), 0, DirectionalBias.UNKNOWN,
                    "No options assessment available for IV rank.",
                    Map.of("state", "MISSING"));
        }
        var volatility = context.options().get().volatility();
        var rank = volatility.impliedVolatilityRank();
        if (rank.isEmpty()) {
            return ScorerSupport.result(id(), 0, DirectionalBias.UNKNOWN,
                    "IV rank is unavailable.",
                    Map.of("status", volatility.status().name()));
        }
        BigDecimal value = rank.get();
        int base = value.compareTo(config.lowIvRank()) <= 0 ? 100
                : value.compareTo(config.highIvRank()) >= 0 ? 30 : 65;
        int points = ScorerSupport.statusAdjusted(base, volatility.status());
        return ScorerSupport.result(id(), points, DirectionalBias.NEUTRAL,
                "IV rank " + ScorerSupport.decimal(value)
                        + " against configured low "
                        + ScorerSupport.decimal(config.lowIvRank())
                        + " and high " + ScorerSupport.decimal(config.highIvRank())
                        + ".",
                Map.of("ivRank", ScorerSupport.decimal(value),
                        "status", volatility.status().name()));
    }
}
