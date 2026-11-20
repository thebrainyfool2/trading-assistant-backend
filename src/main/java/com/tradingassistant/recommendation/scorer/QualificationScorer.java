package com.tradingassistant.recommendation.scorer;

import com.tradingassistant.qualification.domain.QualificationStatus;
import com.tradingassistant.recommendation.api.RecommendationScorer;
import com.tradingassistant.recommendation.domain.DirectionalBias;
import com.tradingassistant.recommendation.domain.RecommendationContext;
import com.tradingassistant.recommendation.domain.ScorerId;
import com.tradingassistant.recommendation.domain.ScorerResult;
import com.tradingassistant.recommendation.rule.ScorerSupport;

import java.util.Map;

/**
 * Scores the strongest existing trade qualification.
 */
public final class QualificationScorer implements RecommendationScorer {

    @Override
    public ScorerId id() {
        return ScorerId.QUALIFICATION;
    }

    @Override
    public ScorerResult score(RecommendationContext context) {
        var qualification = ScorerSupport.preferredQualification(context);
        if (qualification.isEmpty()) {
            return ScorerSupport.result(id(), 0, DirectionalBias.UNKNOWN,
                    "No trade qualification is available.",
                    Map.of("qualifications",
                            Integer.toString(context.qualifications().size())));
        }
        QualificationStatus status = qualification.get().status();
        int points = switch (status) {
            case QUALIFIED -> 100;
            case PARTIAL -> 60;
            case UNKNOWN -> 20;
            case INSUFFICIENT_DATA -> 0;
            case DISQUALIFIED -> 0;
        };
        return ScorerSupport.result(id(), points, DirectionalBias.NEUTRAL,
                qualification.get().summary(),
                Map.of("status", status.name(),
                        "strategyId", qualification.get().strategyId(),
                        "ruleCount",
                        Integer.toString(qualification.get().ruleCount())));
    }
}
