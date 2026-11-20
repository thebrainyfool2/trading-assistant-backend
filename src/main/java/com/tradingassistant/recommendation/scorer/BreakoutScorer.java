package com.tradingassistant.recommendation.scorer;

import com.tradingassistant.indicator.domain.IndicatorValues;
import com.tradingassistant.intelligence.domain.StructureSignal;
import com.tradingassistant.recommendation.api.RecommendationScorer;
import com.tradingassistant.recommendation.domain.DirectionalBias;
import com.tradingassistant.recommendation.domain.RecommendationContext;
import com.tradingassistant.recommendation.domain.ScorerId;
import com.tradingassistant.recommendation.domain.ScorerResult;
import com.tradingassistant.recommendation.rule.ScorerSupport;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Scores a structure breakout signal or a Donchian channel position already
 * computed by the indicator engine.
 */
public final class BreakoutScorer implements RecommendationScorer {

    @Override
    public ScorerId id() {
        return ScorerId.BREAKOUT;
    }

    @Override
    public ScorerResult score(RecommendationContext context) {
        var structure = context.market().structure();
        DirectionalBias bias = DirectionalBias.NEUTRAL;
        int base = 40;
        String reason;
        if (structure.signals().contains(StructureSignal.BREAKOUT_CANDIDATE)) {
            bias = DirectionalBias.BULLISH;
            base = 100;
            reason = "structure reports a breakout candidate";
        } else if (structure.signals().contains(StructureSignal.BREAKDOWN_CANDIDATE)) {
            bias = DirectionalBias.BEARISH;
            base = 100;
            reason = "structure reports a breakdown candidate";
        } else {
            Optional<BigDecimal> close = ScorerSupport.candleClose(context);
            Optional<BigDecimal> upper = ScorerSupport.value(context,
                    "DONCHIAN", IndicatorValues.UPPER);
            Optional<BigDecimal> lower = ScorerSupport.value(context,
                    "DONCHIAN", IndicatorValues.LOWER);
            if (close.isPresent() && upper.isPresent()
                    && close.get().compareTo(upper.get()) >= 0) {
                bias = DirectionalBias.BULLISH;
                base = 90;
                reason = "close is at or above the Donchian upper channel";
            } else if (close.isPresent() && lower.isPresent()
                    && close.get().compareTo(lower.get()) <= 0) {
                bias = DirectionalBias.BEARISH;
                base = 90;
                reason = "close is at or below the Donchian lower channel";
            } else {
                reason = "no breakout or breakdown condition is present";
            }
        }
        int points = ScorerSupport.statusAdjusted(base, structure.status());
        return ScorerSupport.result(id(), points, bias,
                "Breakout read: " + reason + ".",
                Map.of("signals", structure.signals().toString(),
                        "status", structure.status().name()));
    }
}
