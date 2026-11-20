package com.tradingassistant.recommendation.domain;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.intelligence.domain.MarketAssessment;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.options.domain.ChainSnapshot;
import com.tradingassistant.options.domain.OptionsAssessment;
import com.tradingassistant.platform.readmodel.MarketDataFreshnessService;
import com.tradingassistant.qualification.domain.TradeQualification;
import com.tradingassistant.risk.domain.RiskAssessment;
import com.tradingassistant.strategy.domain.StrategyEvaluation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable snapshot of already-computed engine outputs for one instrument.
 * The context is the only input to the recommendation engine; no scorer reaches
 * into a service, a broker or a clock.
 */
public record RecommendationContext(
        MarketAssessment market,
        Optional<OptionsAssessment> options,
        Optional<ChainSnapshot> chain,
        Map<String, IndicatorResult> indicators,
        Optional<Candle> candle,
        List<StrategyEvaluation> strategies,
        List<TradeQualification> qualifications,
        List<RiskAssessment> risks,
        MarketDataFreshnessService.Freshness freshness) {

    public RecommendationContext {
        market = Objects.requireNonNull(market, "market must not be null");
        options = options == null ? Optional.empty() : options;
        chain = chain == null ? Optional.empty() : chain;
        indicators = indicators == null ? Map.of() : Map.copyOf(indicators);
        candle = candle == null ? Optional.empty() : candle;
        strategies = strategies == null ? List.of() : List.copyOf(strategies);
        qualifications = qualifications == null ? List.of() : List.copyOf(qualifications);
        risks = risks == null ? List.of() : List.copyOf(risks);
        freshness = Objects.requireNonNull(freshness,
                "freshness must not be null");
    }
}
