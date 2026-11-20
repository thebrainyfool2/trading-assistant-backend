package com.tradingassistant.monitoring.domain;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.intelligence.domain.MarketAssessment;
import com.tradingassistant.options.domain.OptionsAssessment;
import com.tradingassistant.platform.readmodel.MarketDataFreshnessService.Freshness;
import com.tradingassistant.qualification.domain.TradeQualification;
import com.tradingassistant.recommendation.domain.Recommendation;
import com.tradingassistant.risk.domain.RiskAssessment;
import com.tradingassistant.strategy.domain.StrategyEvaluation;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable snapshot of existing engine outputs used for one monitoring pass.
 * No component in this context recalculates an indicator, an option analytic or
 * a recommendation.
 */
public record TradeMonitoringContext(
        ManualTrade trade,
        Recommendation recommendation,
        MarketAssessment market,
        Map<String, IndicatorResult> indicators,
        OptionsAssessment options,
        StrategyEvaluation strategy,
        TradeQualification qualification,
        RiskAssessment risk,
        Freshness freshness,
        Optional<TradeMonitoringResult> previousResult) {

    public TradeMonitoringContext {
        trade = Objects.requireNonNull(trade, "trade must not be null");
        recommendation = Objects.requireNonNull(recommendation,
                "recommendation must not be null");
        market = Objects.requireNonNull(market, "market must not be null");
        indicators = indicators == null ? Map.of() : Map.copyOf(indicators);
        options = Objects.requireNonNull(options, "options must not be null");
        strategy = Objects.requireNonNull(strategy, "strategy must not be null");
        qualification = Objects.requireNonNull(qualification,
                "qualification must not be null");
        risk = Objects.requireNonNull(risk, "risk must not be null");
        freshness = Objects.requireNonNull(freshness, "freshness must not be null");
        previousResult = previousResult == null ? Optional.empty() : previousResult;
    }
}