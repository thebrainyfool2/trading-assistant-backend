package com.tradingassistant.recommendation.scorer;

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
 * Reads the chain's existing open-interest support and resistance zones and the
 * completed candle close. It does not derive levels from price.
 */
public final class SupportResistanceScorer implements RecommendationScorer {

    @Override
    public ScorerId id() {
        return ScorerId.SUPPORT_RESISTANCE;
    }

    @Override
    public ScorerResult score(RecommendationContext context) {
        if (context.options().isEmpty()) {
            return ScorerSupport.result(id(), 0, DirectionalBias.UNKNOWN,
                    "No options assessment available for support/resistance.",
                    Map.of("state", "MISSING"));
        }
        var openInterest = context.options().get().openInterest();
        Optional<BigDecimal> close = ScorerSupport.candleClose(context);
        if (close.isEmpty() || openInterest.supportZones().isEmpty()
                && openInterest.resistanceZones().isEmpty()) {
            return ScorerSupport.result(id(), 0, DirectionalBias.UNKNOWN,
                    "Support/resistance or completed candle close is unavailable.",
                    Map.of("supportZones",
                            Integer.toString(openInterest.supportZones().size()),
                            "resistanceZones",
                            Integer.toString(openInterest.resistanceZones().size())));
        }
        BigDecimal price = close.get();
        Optional<BigDecimal> highestResistance = openInterest.resistanceZones()
                .stream().max(BigDecimal::compareTo);
        Optional<BigDecimal> lowestSupport = openInterest.supportZones()
                .stream().min(BigDecimal::compareTo);
        DirectionalBias bias = DirectionalBias.NEUTRAL;
        int base = 55;
        String reason;
        if (highestResistance.isPresent()
                && price.compareTo(highestResistance.get()) > 0) {
            bias = DirectionalBias.BULLISH;
            base = 85;
            reason = "close is above every open-interest resistance zone";
        } else if (lowestSupport.isPresent()
                && price.compareTo(lowestSupport.get()) < 0) {
            bias = DirectionalBias.BEARISH;
            base = 85;
            reason = "close is below every open-interest support zone";
        } else if (highestResistance.isPresent() && lowestSupport.isPresent()) {
            base = 65;
            reason = "close is between the nearest support and resistance zones";
        } else if (lowestSupport.isPresent()) {
            bias = DirectionalBias.BULLISH;
            base = 70;
            reason = "close is above the available support zone";
        } else {
            bias = DirectionalBias.BEARISH;
            base = 70;
            reason = "close is below the available resistance zone";
        }
        int points = ScorerSupport.statusAdjusted(base, openInterest.status());
        return ScorerSupport.result(id(), points, bias,
                "Support/resistance read: " + reason + ".",
                Map.of("close", ScorerSupport.decimal(price),
                        "supportZones",
                        Integer.toString(openInterest.supportZones().size()),
                        "resistanceZones",
                        Integer.toString(openInterest.resistanceZones().size())));
    }
}
