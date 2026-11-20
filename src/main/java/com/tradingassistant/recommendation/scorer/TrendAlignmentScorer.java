package com.tradingassistant.recommendation.scorer;

import com.tradingassistant.indicator.domain.IndicatorValues;
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
 * Checks whether trend, structure, Supertrend and EMA already agree on one
 * direction. Every input value is read from an existing assessment or indicator.
 */
public final class TrendAlignmentScorer implements RecommendationScorer {

    @Override
    public ScorerId id() {
        return ScorerId.TREND_ALIGNMENT;
    }

    @Override
    public ScorerResult score(RecommendationContext context) {
        DirectionalBias trendBias = ScorerSupport.trendBias(context.market());
        DirectionalBias structureBias = ScorerSupport.structureBias(context.market());
        DirectionalBias expected = trendBias == DirectionalBias.BULLISH
                || trendBias == DirectionalBias.BEARISH
                ? trendBias : structureBias;
        if (expected != DirectionalBias.BULLISH
                && expected != DirectionalBias.BEARISH) {
            return ScorerSupport.result(id(), 0, DirectionalBias.NEUTRAL,
                    "No directional trend or structure is available for alignment.",
                    Map.of("trendBias", trendBias.name(),
                            "structureBias", structureBias.name()));
        }
        int aligned = 0;
        int observed = 0;
        if (trendBias == expected) {
            aligned++;
            observed++;
        }
        if (structureBias == expected) {
            aligned++;
            observed++;
        }
        Optional<BigDecimal> supertrendDirection = ScorerSupport.value(context,
                "SUPERTREND", IndicatorValues.DIRECTION);
        if (supertrendDirection.isPresent()) {
            observed++;
            if ((supertrendDirection.get().signum() > 0
                    && expected == DirectionalBias.BULLISH)
                    || (supertrendDirection.get().signum() < 0
                    && expected == DirectionalBias.BEARISH)) {
                aligned++;
            }
        }
        Optional<BigDecimal> ema = ScorerSupport.value(context, "EMA",
                IndicatorValues.VALUE);
        Optional<BigDecimal> close = ScorerSupport.candleClose(context);
        if (ema.isPresent() && close.isPresent()) {
            observed++;
            if ((close.get().compareTo(ema.get()) > 0
                    && expected == DirectionalBias.BULLISH)
                    || (close.get().compareTo(ema.get()) < 0
                    && expected == DirectionalBias.BEARISH)) {
                aligned++;
            }
        }
        int base = aligned == 4 ? 100
                : aligned == 3 ? 80
                : aligned == 2 ? 60
                : aligned == 1 ? 30 : 0;
        int points = ScorerSupport.statusAdjusted(base,
                context.market().trend().status());
        return ScorerSupport.result(id(), points, expected,
                "Trend alignment: " + aligned + " of 4 available inputs agree with "
                        + expected.name().toLowerCase() + ".",
                Map.of("trendBias", trendBias.name(),
                        "structureBias", structureBias.name(),
                        "alignedInputs", Integer.toString(aligned),
                        "observedInputs", Integer.toString(observed),
                        "requiredInputs", "4"));
    }
}
