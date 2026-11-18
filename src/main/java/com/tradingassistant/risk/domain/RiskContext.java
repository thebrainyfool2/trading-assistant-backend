package com.tradingassistant.risk.domain;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.domain.IndicatorValues;
import com.tradingassistant.intelligence.domain.MarketAssessment;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.options.domain.OptionsAssessment;
import com.tradingassistant.qualification.domain.TradeQualification;
import com.tradingassistant.risk.exception.RiskValidationException;
import com.tradingassistant.strategy.domain.StrategyEvaluation;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything one risk assessment is allowed to read.
 *
 * <p>The context is immutable and closed: domain objects only, no broker object,
 * no HTTP request, no database handle and no persistence hook. Every part must
 * describe the same instrument, interval and instant, so a risk boundary can never
 * be computed from values captured at different moments.</p>
 *
 * <p>An option assessment of another instant is rejected rather than silently
 * used: the qualification layer already refused to qualify misaligned data, so a
 * stale chain here would mean the pipeline is inconsistent.</p>
 *
 * @param strategyEvaluation the strategy outcome that was qualified
 * @param qualification      the qualification produced for that outcome
 * @param marketAssessment   description of the market for the same candle
 * @param optionsAssessment  optional description of the derivatives market
 * @param candle             optional current candle of the same instant
 * @param indicators         latest indicator results keyed by uppercase type
 */
public record RiskContext(
        StrategyEvaluation strategyEvaluation,
        TradeQualification qualification,
        MarketAssessment marketAssessment,
        Optional<OptionsAssessment> optionsAssessment,
        Optional<Candle> candle,
        Map<String, IndicatorResult> indicators) {

    public RiskContext {
        strategyEvaluation = Objects.requireNonNull(strategyEvaluation,
                "strategyEvaluation must not be null");
        qualification = Objects.requireNonNull(qualification, "qualification must not be null");
        marketAssessment = Objects.requireNonNull(marketAssessment,
                "marketAssessment must not be null");
        optionsAssessment = optionsAssessment == null ? Optional.empty() : optionsAssessment;
        candle = candle == null ? Optional.empty() : candle;

        StrategyEvaluation evaluation = strategyEvaluation;
        InstrumentKey instrument = evaluation.instrument();
        requireSameInstrument(instrument, marketAssessment.instrument());
        if (!qualification.strategyId().equals(evaluation.strategyId())) {
            throw new RiskValidationException(
                    "strategyId of the qualification must match the strategy evaluation");
        }
        if (!qualification.instrument().equals(instrument)) {
            throw new RiskValidationException(
                    "instrument of the qualification must match the strategy evaluation");
        }
        if (!marketAssessment.timestamp().equals(evaluation.timestamp())) {
            throw new RiskValidationException(
                    "timestamp of the market assessment must match the strategy evaluation");
        }
        if (!qualification.timestamp().equals(evaluation.timestamp())) {
            throw new RiskValidationException(
                    "timestamp of the qualification must match the strategy evaluation");
        }
        if (marketAssessment.interval() != evaluation.interval()) {
            throw new RiskValidationException(
                    "interval of the market assessment must match the strategy evaluation");
        }
        if (qualification.interval() != evaluation.interval()) {
            throw new RiskValidationException(
                    "interval of the qualification must match the strategy evaluation");
        }

        Optional<OptionsAssessment> options = optionsAssessment;
        options.ifPresent(value -> {
            if (!value.spotInstrument().equals(instrument)) {
                throw new RiskValidationException(
                        "instrument of the options assessment must match the strategy evaluation");
            }
            if (!value.timestamp().equals(evaluation.timestamp())) {
                throw new RiskValidationException(
                        "timestamp of the options assessment must match the strategy evaluation");
            }
        });

        Optional<Candle> current = candle;
        current.ifPresent(value -> {
            if (!value.instrument().equals(instrument)) {
                throw new RiskValidationException(
                        "instrument of the candle must match the strategy evaluation");
            }
            if (value.interval() != evaluation.interval()) {
                throw new RiskValidationException(
                        "interval of the candle must match the strategy evaluation");
            }
            if (!value.timestamp().equals(evaluation.timestamp())) {
                throw new RiskValidationException(
                        "timestamp of the candle must match the strategy evaluation");
            }
        });

        Map<String, IndicatorResult> canonical = new LinkedHashMap<>();
        if (indicators != null) {
            indicators.forEach((type, result) -> {
                if (type == null || type.isBlank() || result == null) {
                    throw new RiskValidationException(
                            "indicators must not contain null or blank entries");
                }
                canonical.put(type.trim().toUpperCase(java.util.Locale.ROOT), result);
            });
        }
        indicators = Map.copyOf(canonical);
        requireValidAtr(indicators);
    }

    public InstrumentKey instrument() {
        return strategyEvaluation.instrument();
    }

    public Interval interval() {
        return strategyEvaluation.interval();
    }

    public java.time.Instant timestamp() {
        return strategyEvaluation.timestamp();
    }

    /**
     * Value of one indicator key, when the indicator and key are present.
     */
    public Optional<BigDecimal> indicatorValue(String type, String key) {
        if (type == null || type.isBlank() || key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(indicators.get(type.trim().toUpperCase(
                        java.util.Locale.ROOT)))
                .flatMap(result -> result.value(key));
    }

    /**
     * Average true range, when the ATR indicator is present.
     */
    public Optional<BigDecimal> atr() {
        return indicatorValue("ATR", IndicatorValues.VALUE);
    }

    /**
     * Reference price: the candle close when a candle exists, otherwise the spot
     * price of the option chain.
     */
    public Optional<BigDecimal> price() {
        return candle.map(Candle::close)
                .or(() -> optionsAssessment.map(options -> options.chain().spotPrice()));
    }

    public boolean hasOptions() {
        return optionsAssessment.isPresent();
    }

    private static void requireSameInstrument(InstrumentKey expected, InstrumentKey actual) {
        if (!expected.equals(actual)) {
            throw new RiskValidationException(
                    "instrument of the market assessment must match the strategy evaluation");
        }
    }

    private static void requireValidAtr(Map<String, IndicatorResult> indicators) {
        IndicatorResult atr = indicators.get("ATR");
        if (atr == null) {
            return;
        }
        BigDecimal value = atr.values().get(IndicatorValues.VALUE);
        if (value != null && value.signum() < 0) {
            throw new RiskValidationException("ATR must not be negative");
        }
    }
}
