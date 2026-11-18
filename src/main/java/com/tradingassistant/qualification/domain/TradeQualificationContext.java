package com.tradingassistant.qualification.domain;

import com.tradingassistant.intelligence.domain.MarketAssessment;
import com.tradingassistant.intelligence.domain.SessionAssessment;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.options.domain.OptionsAssessment;
import com.tradingassistant.qualification.exception.QualificationValidationException;
import com.tradingassistant.strategy.domain.StrategyEvaluation;

import java.util.Objects;
import java.util.Optional;

/**
 * Everything one qualification decision is allowed to read.
 *
 * <p>The context is immutable and closed: it holds domain objects only. There is
 * no broker object, no HTTP request, no database handle and no persistence hook,
 * so a rule cannot reach outside the description it was given.</p>
 *
 * <p>The strategy evaluation, the market assessment and — when present — the
 * option assessment and candle must describe the same instrument, interval and
 * instant. A qualification may not mix values from different moments, so the
 * constructor rejects such a mix instead of letting a rule decide on stale data.
 * The one deliberate exception is the option assessment timestamp: whether it is
 * stale is a qualification condition, reported by a rule, rather than a reason to
 * refuse the context.</p>
 *
 * @param strategyEvaluation outcome produced by the strategy layer
 * @param marketAssessment  description of the market for the same candle
 * @param optionsAssessment optional description of the derivatives market
 * @param session           session context of the assessed candle
 * @param candle            optional current candle of the same instant
 */
public record TradeQualificationContext(
        StrategyEvaluation strategyEvaluation,
        MarketAssessment marketAssessment,
        Optional<OptionsAssessment> optionsAssessment,
        SessionAssessment session,
        Optional<Candle> candle) {

    public TradeQualificationContext {
        strategyEvaluation = Objects.requireNonNull(strategyEvaluation,
                "strategyEvaluation must not be null");
        marketAssessment = Objects.requireNonNull(marketAssessment,
                "marketAssessment must not be null");
        optionsAssessment = optionsAssessment == null ? Optional.empty() : optionsAssessment;
        session = Objects.requireNonNull(session, "session must not be null");
        candle = candle == null ? Optional.empty() : candle;

        StrategyEvaluation evaluation = strategyEvaluation;
        InstrumentKey instrument = evaluation.instrument();
        if (!marketAssessment.instrument().equals(instrument)) {
            throw new QualificationValidationException(
                    "instrument of the strategy evaluation and the market assessment must match");
        }
        if (!marketAssessment.timestamp().equals(evaluation.timestamp())) {
            throw new QualificationValidationException(
                    "timestamp of the strategy evaluation and the market assessment must match");
        }
        if (marketAssessment.interval() != evaluation.interval()) {
            throw new QualificationValidationException(
                    "interval of the strategy evaluation and the market assessment must match");
        }
        if (!session.phase().equals(marketAssessment.session().phase())) {
            throw new QualificationValidationException(
                    "session phase must match the market assessment session");
        }
        Optional<OptionsAssessment> options = optionsAssessment;
        options.ifPresent(value -> {
            if (!value.spotInstrument().equals(instrument)) {
                throw new QualificationValidationException(
                        "instrument of the options assessment must match the strategy evaluation");
            }
        });
        Optional<Candle> currentCandle = candle;
        currentCandle.ifPresent(value -> {
            if (!value.instrument().equals(instrument)) {
                throw new QualificationValidationException(
                        "instrument of the candle must match the strategy evaluation");
            }
            if (value.interval() != evaluation.interval()) {
                throw new QualificationValidationException(
                        "interval of the candle must match the strategy evaluation");
            }
            if (!value.timestamp().equals(evaluation.timestamp())) {
                throw new QualificationValidationException(
                        "timestamp of the candle must match the strategy evaluation");
            }
        });
    }

    /**
     * Builds a context that inherits the session of the market assessment.
     */
    public static TradeQualificationContext of(StrategyEvaluation evaluation,
                                               MarketAssessment market,
                                               OptionsAssessment options,
                                               Candle candle) {
        return new TradeQualificationContext(evaluation, market,
                options == null ? Optional.empty() : Optional.of(options),
                market.session(),
                candle == null ? Optional.empty() : Optional.of(candle));
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

    public boolean hasOptions() {
        return optionsAssessment.isPresent();
    }
}
