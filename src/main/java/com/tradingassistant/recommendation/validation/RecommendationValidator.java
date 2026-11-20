package com.tradingassistant.recommendation.validation;

import com.tradingassistant.platform.readmodel.MarketDataFreshnessService;
import com.tradingassistant.recommendation.domain.RecommendationContext;
import com.tradingassistant.recommendation.exception.RecommendationValidationException;

import java.util.List;
import java.util.Objects;

/**
 * Rejects incomplete or misaligned inputs before any scorer runs.
 */
public final class RecommendationValidator {

    public RecommendationContext align(RecommendationContext context) {
        Objects.requireNonNull(context, "context must not be null");
        var market = context.market();
        if (market.status()
                == com.tradingassistant.intelligence.domain.AssessmentStatus.INSUFFICIENT_DATA) {
            throw new RecommendationValidationException(
                    "market assessment has insufficient data");
        }
        var freshness = context.freshness();
        if (!freshness.instrument().equals(market.instrument())) {
            throw new RecommendationValidationException(
                    "freshness does not match the market instrument");
        }
        if (freshness.status()
                != MarketDataFreshnessService.FreshnessStatus.FRESH) {
            throw new RecommendationValidationException(
                    "market data is not fresh: " + freshness.status());
        }
        context.options().ifPresent(options -> {
            if (!options.spotInstrument().equals(market.instrument())) {
                throw new RecommendationValidationException(
                        "options assessment does not match the market instrument");
            }
            if (!options.timestamp().equals(market.timestamp())) {
                throw new RecommendationValidationException(
                        "options assessment does not match the market timestamp");
            }
        });
        context.chain().ifPresent(chain -> {
            if (!chain.spotInstrument().equals(market.instrument())) {
                throw new RecommendationValidationException(
                        "option chain does not match the market instrument");
            }
            if (!chain.capturedAt().equals(market.timestamp())) {
                throw new RecommendationValidationException(
                        "option chain does not match the market timestamp");
            }
        });
        List<com.tradingassistant.strategy.domain.StrategyEvaluation> strategies =
                context.strategies().stream()
                        .filter(strategy -> strategy.instrument().equals(market.instrument())
                                && strategy.interval() == market.interval()
                                && strategy.timestamp().equals(market.timestamp()))
                        .toList();
        List<com.tradingassistant.qualification.domain.TradeQualification> qualifications =
                context.qualifications().stream()
                        .filter(qualification -> qualification.instrument()
                                .equals(market.instrument())
                                && qualification.interval() == market.interval()
                                && qualification.timestamp().equals(market.timestamp()))
                        .toList();
        if (qualifications.isEmpty()) {
            throw new RecommendationValidationException(
                    "no trade qualification matches the market timestamp");
        }
        List<com.tradingassistant.risk.domain.RiskAssessment> risks =
                context.risks().stream()
                        .filter(risk -> risk.instrument().equals(market.instrument())
                                && risk.interval() == market.interval()
                                && risk.timestamp().equals(market.timestamp()))
                        .toList();
        return new RecommendationContext(market, context.options(), context.chain(),
                context.indicators(), context.candle(), strategies, qualifications, risks,
                freshness);
    }
}
