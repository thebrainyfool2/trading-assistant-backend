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
 * Scores the existing risk reward comparison against the floor that the risk
 * engine already applied.
 */
public final class RiskRewardScorer implements RecommendationScorer {

    @Override
    public ScorerId id() {
        return ScorerId.RISK_REWARD;
    }

    @Override
    public ScorerResult score(RecommendationContext context) {
        var risk = ScorerSupport.preferredRisk(context);
        if (risk.isEmpty()) {
            return ScorerSupport.result(id(), 0, DirectionalBias.UNKNOWN,
                    "No risk assessment within limits has an achieved reward/risk ratio.",
                    Map.of("riskAssessments",
                            Integer.toString(context.risks().size())));
        }
        var riskReward = risk.get().riskReward();
        BigDecimal ratio = riskReward.achievedRatio().orElse(BigDecimal.ZERO);
        BigDecimal minimum = riskReward.minimumRatio();
        int base = ratio.compareTo(minimum) >= 0 ? 100
                : ratio.compareTo(minimum.multiply(BigDecimal.valueOf(0.75))) >= 0
                ? 70 : 30;
        return ScorerSupport.result(id(), base, DirectionalBias.NEUTRAL,
                "Achieved reward/risk " + ScorerSupport.decimal(ratio)
                        + " against configured minimum "
                        + ScorerSupport.decimal(minimum) + ".",
                Map.of("achievedRatio", ScorerSupport.decimal(ratio),
                        "minimumRatio", ScorerSupport.decimal(minimum),
                        "riskStatus", risk.get().status().name()));
    }
}
