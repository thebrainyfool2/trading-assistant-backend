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
 * Compares the existing option expected move with the existing risk stop
 * distance. Both values are read, not derived.
 */
public final class ExpectedMoveScorer implements RecommendationScorer {

    @Override
    public ScorerId id() {
        return ScorerId.EXPECTED_MOVE;
    }

    @Override
    public ScorerResult score(RecommendationContext context) {
        if (context.options().isEmpty()) {
            return ScorerSupport.result(id(), 0, DirectionalBias.UNKNOWN,
                    "No options assessment available for expected move.",
                    Map.of("state", "MISSING"));
        }
        var expectedMove = context.options().get().expectedMove();
        if (expectedMove.expiryMove().isEmpty()) {
            return ScorerSupport.result(id(), 0, DirectionalBias.UNKNOWN,
                    "Expiry expected move is unavailable.",
                    Map.of("status", expectedMove.status().name()));
        }
        BigDecimal move = expectedMove.expiryMove().get();
        var stopDistance = context.risks().stream()
                .filter(risk -> risk.withinLimits() && risk.stopDistance().signum() > 0)
                .map(risk -> risk.stopDistance())
                .findFirst();
        int base;
        String comparison;
        if (stopDistance.isEmpty()) {
            base = 70;
            comparison = "no risk stop distance is available";
        } else if (move.compareTo(stopDistance.get()) >= 0) {
            base = 100;
            comparison = "expected move covers the stop distance";
        } else if (move.compareTo(stopDistance.get()
                .multiply(BigDecimal.valueOf(0.75))) >= 0) {
            base = 75;
            comparison = "expected move covers at least three quarters of the stop distance";
        } else {
            base = 50;
            comparison = "expected move is smaller than the stop distance";
        }
        int points = ScorerSupport.statusAdjusted(base, expectedMove.status());
        return ScorerSupport.result(id(), points, DirectionalBias.NEUTRAL,
                "Expiry expected move " + ScorerSupport.decimal(move) + "; "
                        + comparison + ".",
                Map.of("expiryMove", ScorerSupport.decimal(move),
                        "stopDistance", stopDistance
                                .map(ScorerSupport::decimal).orElse("unknown"),
                        "status", expectedMove.status().name()));
    }
}
