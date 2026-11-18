package com.tradingassistant.strategy.domain;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.intelligence.domain.MarketAssessment;
import com.tradingassistant.intelligence.domain.SessionAssessment;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.options.domain.OptionsAssessment;
import com.tradingassistant.strategy.exception.StrategyValidationException;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The single validated input boundary of the strategy engine.
 *
 * <p>The context ties one market assessment to the candle, indicator values and
 * option assessment of the same instrument and instant. Everything is immutable
 * and copied on construction, and the session context is derived from the market
 * assessment instead of being stored twice.</p>
 *
 * <p>An indicator or candle that belongs to another instrument or another
 * timestamp is rejected rather than silently ignored: a rule must never compare
 * values from different moments.</p>
 *
 * @param marketAssessment   market description of the evaluated instant
 * @param optionsAssessment  option description of the same instrument and instant
 * @param candle             completed candle of the evaluated instant
 * @param indicators         latest indicator results keyed by canonical type
 */
public record StrategyEvaluationContext(
        MarketAssessment marketAssessment,
        Optional<OptionsAssessment> optionsAssessment,
        Optional<Candle> candle,
        Map<String, IndicatorResult> indicators) {

    public StrategyEvaluationContext {
        marketAssessment = Objects.requireNonNull(marketAssessment,
                "marketAssessment must not be null");
        optionsAssessment = optionsAssessment == null ? Optional.empty() : optionsAssessment;
        candle = candle == null ? Optional.empty() : candle;
        indicators = indicators == null ? Map.of() : canonicalIndicators(indicators);
        MarketAssessment assessment = marketAssessment;

        optionsAssessment.ifPresent(options -> {
            if (!options.spotInstrument().equals(assessment.instrument())) {
                throw new StrategyValidationException(
                        "optionsAssessment must describe the same instrument");
            }
            if (!options.timestamp().equals(assessment.timestamp())) {
                throw new StrategyValidationException(
                        "optionsAssessment timestamp must match the market assessment");
            }
        });
        candle.ifPresent(value -> {
            if (!value.instrument().equals(assessment.instrument())) {
                throw new StrategyValidationException(
                        "candle must describe the same instrument");
            }
            if (!value.timestamp().equals(assessment.timestamp())) {
                throw new StrategyValidationException(
                        "candle timestamp must match the market assessment");
            }
            if (value.interval() != assessment.interval()) {
                throw new StrategyValidationException(
                        "candle interval must match the market assessment");
            }
        });
        for (IndicatorResult result : indicators.values()) {
            if (!result.timestamp().equals(assessment.timestamp())) {
                throw new StrategyValidationException(
                        "indicator " + result.indicator()
                                + " timestamp must match the market assessment");
            }
            for (Map.Entry<String, java.math.BigDecimal> entry : result.values().entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    throw new StrategyValidationException("indicator " + result.indicator()
                            + " values must not contain null");
                }
            }
        }
    }

    public static StrategyEvaluationContext of(MarketAssessment assessment) {
        return new StrategyEvaluationContext(assessment, Optional.empty(), Optional.empty(),
                Map.of());
    }

    /**
     * Session context of the assessed candle.
     */
    public SessionAssessment session() {
        return marketAssessment.session();
    }

    public Optional<IndicatorResult> indicator(String type) {
        if (type == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(indicators.get(type.trim().toUpperCase(Locale.ROOT)));
    }

    private static Map<String, IndicatorResult> canonicalIndicators(
            Map<String, IndicatorResult> indicators) {
        Map<String, IndicatorResult> canonical = new TreeMap<>();
        for (Map.Entry<String, IndicatorResult> entry : indicators.entrySet()) {
            String key = entry.getKey();
            IndicatorResult result = entry.getValue();
            if (key == null || key.isBlank()) {
                throw new StrategyValidationException("indicator key must not be blank");
            }
            if (result == null) {
                throw new StrategyValidationException(
                        "indicator " + key + " must not be null");
            }
            String normalized = key.trim().toUpperCase(Locale.ROOT);
            if (!result.indicator().equalsIgnoreCase(normalized)) {
                throw new StrategyValidationException(
                        "indicator key " + key + " must match its result type");
            }
            if (canonical.putIfAbsent(normalized, result) != null) {
                throw new StrategyValidationException(
                        "duplicate indicator " + normalized);
            }
        }
        return Map.copyOf(canonical);
    }
}
