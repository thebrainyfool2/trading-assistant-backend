package com.tradingassistant.recommendation.rule;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.intelligence.domain.MarketAssessment;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.options.domain.ExpiryChain;
import com.tradingassistant.options.domain.StrikeGroup;
import com.tradingassistant.qualification.domain.QualificationStatus;
import com.tradingassistant.qualification.domain.TradeQualification;
import com.tradingassistant.recommendation.domain.DirectionalBias;
import com.tradingassistant.recommendation.domain.RecommendationContext;
import com.tradingassistant.recommendation.domain.ScorerId;
import com.tradingassistant.recommendation.domain.ScorerResult;
import com.tradingassistant.risk.domain.RiskAssessment;
import com.tradingassistant.risk.domain.RiskStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Shared deterministic primitives for scorers. Nothing here calculates an
 * indicator, a Greek, an IV, a risk number or a market statistic: it only reads
 * values that an existing engine already produced.
 */
public final class ScorerSupport {

    private ScorerSupport() {
    }

    public static ScorerResult result(ScorerId id, int points, DirectionalBias bias,
                                      String explanation, Map<String, String> diagnostics) {
        int bounded = clamp(points, 0, 100);
        return new ScorerResult(id, BigDecimal.valueOf(bounded),
                BigDecimal.valueOf(100), Objects.requireNonNull(bias,
                "bias must not be null"), explanation, diagnostics);
    }

    public static ScorerResult result(ScorerId id, int points, DirectionalBias bias,
                                      String explanation) {
        return result(id, points, bias, explanation, Map.of());
    }

    public static int statusAdjusted(int base, AssessmentStatus status) {
        if (status == null) {
            return 0;
        }
        return switch (status) {
            case READY -> clamp(base, 0, 100);
            case PARTIAL -> clamp((int) Math.round(base * 0.75), 0, 100);
            case UNKNOWN -> clamp((int) Math.round(base * 0.50), 0, 100);
            case INSUFFICIENT_DATA -> 0;
        };
    }

    public static DirectionalBias trendBias(MarketAssessment market) {
        return switch (market.trend().state()) {
            case BULLISH -> DirectionalBias.BULLISH;
            case BEARISH -> DirectionalBias.BEARISH;
            case SIDEWAYS -> DirectionalBias.NEUTRAL;
            case UNKNOWN -> DirectionalBias.UNKNOWN;
        };
    }

    public static DirectionalBias structureBias(MarketAssessment market) {
        return switch (market.structure().bias()) {
            case UPTREND -> DirectionalBias.BULLISH;
            case DOWNTREND -> DirectionalBias.BEARISH;
            case RANGING -> DirectionalBias.NEUTRAL;
            case UNKNOWN -> DirectionalBias.UNKNOWN;
        };
    }

    public static DirectionalBias opposite(DirectionalBias bias) {
        return switch (bias) {
            case BULLISH -> DirectionalBias.BEARISH;
            case BEARISH -> DirectionalBias.BULLISH;
            case NEUTRAL, UNKNOWN -> bias;
        };
    }

    public static Optional<IndicatorResult> indicator(RecommendationContext context,
                                                      String type) {
        if (type == null) {
            return Optional.empty();
        }
        return context.indicators().values().stream()
                .filter(value -> value.indicator().equalsIgnoreCase(type))
                .findFirst();
    }

    public static Optional<BigDecimal> value(RecommendationContext context, String type,
                                             String key) {
        return indicator(context, type).flatMap(result -> result.value(key));
    }

    public static Optional<BigDecimal> candleClose(RecommendationContext context) {
        return context.candle().map(Candle::close);
    }

    public static Optional<BigDecimal> nearestStrike(ExpiryChain expiry,
                                                     BigDecimal price) {
        if (expiry == null || price == null) {
            return Optional.empty();
        }
        return expiry.nearestTo(price).map(StrikeGroup::strike);
    }

    public static Optional<RiskAssessment> preferredRisk(RecommendationContext context) {
        return context.risks().stream()
                .filter(Objects::nonNull)
                .filter(RiskAssessment::withinLimits)
                .filter(risk -> risk.riskReward().achievedRatio().isPresent())
                .findFirst();
    }

    public static Optional<TradeQualification> preferredQualification(
            RecommendationContext context) {
        return context.qualifications().stream()
                .filter(Objects::nonNull)
                .min((left, right) -> Integer.compare(
                        rank(left.status()), rank(right.status())))
                .filter(qualification -> qualification.status()
                        != QualificationStatus.DISQUALIFIED);
    }

    public static int rank(QualificationStatus status) {
        return switch (status) {
            case QUALIFIED -> 0;
            case PARTIAL -> 1;
            case UNKNOWN -> 2;
            case INSUFFICIENT_DATA -> 3;
            case DISQUALIFIED -> 4;
        };
    }

    public static int rank(RiskStatus status) {
        return switch (status) {
            case WITHIN_LIMITS -> 0;
            case PARTIAL -> 1;
            case UNKNOWN -> 2;
            case INSUFFICIENT_DATA -> 3;
            case LIMIT_BREACHED -> 4;
        };
    }

    public static Map<String, String> diagnostics(String... keyValues) {
        if (keyValues == null || keyValues.length == 0) {
            return Map.of();
        }
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("diagnostics require key/value pairs");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            values.put(keyValues[index], keyValues[index + 1]);
        }
        return Map.copyOf(values);
    }

    public static String joinIndicators(RecommendationContext context) {
        List<String> names = context.indicators().values().stream()
                .map(IndicatorResult::indicator)
                .sorted()
                .toList();
        return names.isEmpty() ? "none" : String.join(",", names);
    }

    public static String decimal(BigDecimal value) {
        return value == null ? "unknown"
                : value.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros()
                .toPlainString();
    }

    public static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
