package com.tradingassistant.qualification;

import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.intelligence.domain.Confidence;
import com.tradingassistant.intelligence.domain.ConfidenceLevel;
import com.tradingassistant.intelligence.domain.MarketAssessment;
import com.tradingassistant.intelligence.domain.MomentumAssessment;
import com.tradingassistant.intelligence.domain.MomentumState;
import com.tradingassistant.intelligence.domain.ParticipationAssessment;
import com.tradingassistant.intelligence.domain.ParticipationState;
import com.tradingassistant.intelligence.domain.SessionAssessment;
import com.tradingassistant.intelligence.domain.SessionPhase;
import com.tradingassistant.intelligence.domain.StructureAssessment;
import com.tradingassistant.intelligence.domain.StructureBias;
import com.tradingassistant.intelligence.domain.StructureSignal;
import com.tradingassistant.intelligence.domain.TrendAssessment;
import com.tradingassistant.intelligence.domain.TrendState;
import com.tradingassistant.intelligence.domain.VolatilityAssessment;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.options.OptionsTestFixtures;
import com.tradingassistant.options.domain.ChainSummary;
import com.tradingassistant.options.domain.ExpectedMoveMethod;
import com.tradingassistant.options.domain.ExpectedMoveSummary;
import com.tradingassistant.options.domain.Greeks;
import com.tradingassistant.options.domain.GreeksSummary;
import com.tradingassistant.options.domain.LiquidityState;
import com.tradingassistant.options.domain.LiquiditySummary;
import com.tradingassistant.options.domain.MaxPainSummary;
import com.tradingassistant.options.domain.OpenInterestSummary;
import com.tradingassistant.options.domain.OptionsAssessment;
import com.tradingassistant.options.domain.PcrAggregation;
import com.tradingassistant.options.domain.PutCallRatioSummary;
import com.tradingassistant.options.domain.Tradability;
import com.tradingassistant.options.domain.VolatilitySummary;
import com.tradingassistant.qualification.domain.QualificationLedger;
import com.tradingassistant.qualification.domain.TradeQualificationContext;
import com.tradingassistant.strategy.domain.EvaluationStatus;
import com.tradingassistant.strategy.domain.RuleEvaluation;
import com.tradingassistant.strategy.domain.StrategyEvaluation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable inputs for qualification tests.
 *
 * <p>The defaults describe a qualification-friendly situation: a passed strategy
 * evaluation, a ready market assessment with high confidence and average
 * participation, an open session and no option assessment. Individual tests vary
 * exactly the field they are about.</p>
 */
public final class QualificationTestFixtures {

    public static final InstrumentKey SPOT = OptionsTestFixtures.SPOT;
    public static final Instant START = OptionsTestFixtures.START;
    public static final Instant LATER = START.plusSeconds(60);
    public static final String STRATEGY_ID = "EMA_TREND";
    public static final String STRATEGY_NAME = "EMA Trend";

    private QualificationTestFixtures() {
    }

    public static Candle candle() {
        return candle(SPOT, START, Interval.ONE_MINUTE);
    }

    public static Candle candle(Instant timestamp) {
        return candle(SPOT, timestamp, Interval.ONE_MINUTE);
    }

    public static Candle candle(InstrumentKey instrument, Instant timestamp, Interval interval) {
        BigDecimal close = new BigDecimal("105");
        return new Candle(instrument, interval, timestamp,
                close, close.add(BigDecimal.ONE), close.subtract(BigDecimal.ONE), close,
                200_000L);
    }

    public static SessionAssessment session(SessionPhase phase, AssessmentStatus status) {
        return new SessionAssessment(phase, status, Optional.empty(), "test session");
    }

    public static MarketAssessment marketAssessment() {
        return marketAssessment(AssessmentStatus.READY, ConfidenceLevel.HIGH,
                ParticipationState.AVERAGE, SessionPhase.REGULAR);
    }

    public static MarketAssessment marketAssessment(ConfidenceLevel confidence) {
        return marketAssessment(AssessmentStatus.READY, confidence,
                ParticipationState.AVERAGE, SessionPhase.REGULAR);
    }

    public static MarketAssessment marketAssessment(ParticipationState participation) {
        return marketAssessment(AssessmentStatus.READY, ConfidenceLevel.HIGH,
                participation, SessionPhase.REGULAR);
    }

    public static MarketAssessment marketAssessment(SessionPhase phase) {
        return marketAssessment(AssessmentStatus.READY, ConfidenceLevel.HIGH,
                ParticipationState.AVERAGE, phase);
    }

    public static MarketAssessment marketAssessment(AssessmentStatus status,
                                                    ConfidenceLevel confidence,
                                                    ParticipationState participation,
                                                    SessionPhase phase) {
        return marketAssessment(SPOT, START, Interval.ONE_MINUTE, status, confidence,
                participation, phase);
    }

    public static MarketAssessment marketAssessment(InstrumentKey instrument,
                                                    Instant timestamp) {
        return marketAssessment(instrument, timestamp, Interval.ONE_MINUTE,
                AssessmentStatus.READY, ConfidenceLevel.HIGH, ParticipationState.AVERAGE,
                SessionPhase.REGULAR);
    }

    public static MarketAssessment marketAssessment(InstrumentKey instrument,
                                                    Instant timestamp,
                                                    Interval interval) {
        return marketAssessment(instrument, timestamp, interval, AssessmentStatus.READY,
                ConfidenceLevel.HIGH, ParticipationState.AVERAGE, SessionPhase.REGULAR);
    }

    public static MarketAssessment marketAssessment(InstrumentKey instrument,
                                                    Instant timestamp,
                                                    Interval interval,
                                                    AssessmentStatus status,
                                                    ConfidenceLevel confidence,
                                                    ParticipationState participation,
                                                    SessionPhase phase) {
        return new MarketAssessment(instrument, interval, timestamp,
                status, status != AssessmentStatus.READY,
                new Confidence(confidence, "test confidence"),
                new TrendAssessment(TrendState.SIDEWAYS, AssessmentStatus.READY, Set.of(),
                        "test trend"),
                new MomentumAssessment(MomentumState.NEUTRAL, AssessmentStatus.READY, Set.of(),
                        "test momentum"),
                new VolatilityAssessment(
                        com.tradingassistant.intelligence.domain.VolatilityState.STABLE,
                        AssessmentStatus.READY, Set.of(), "test volatility"),
                new ParticipationAssessment(participation, AssessmentStatus.READY, Set.of(),
                        "test participation"),
                new StructureAssessment(StructureBias.RANGING, Set.of(StructureSignal.RANGE),
                        AssessmentStatus.READY, "test structure"),
                session(phase, AssessmentStatus.READY));
    }

    public static StrategyEvaluation strategyEvaluation() {
        return strategyEvaluation(EvaluationStatus.PASSED);
    }

    public static StrategyEvaluation strategyEvaluation(EvaluationStatus status) {
        return strategyEvaluation(STRATEGY_ID, STRATEGY_NAME, status);
    }

    public static StrategyEvaluation strategyEvaluation(String strategyId, String strategyName,
                                                       EvaluationStatus status) {
        return new StrategyEvaluation(strategyId, strategyName, START, SPOT,
                Interval.ONE_MINUTE, status,
                List.of(new RuleEvaluation("EMA.closeAbove", EvaluationStatus.PASSED,
                        "close is above EMA", Map.of())),
                "1 of 1 rules passed");
    }

    public static OptionsAssessment optionsAssessment() {
        return optionsAssessment(START, session(SessionPhase.REGULAR, AssessmentStatus.READY),
                LiquidityState.LIQUID, AssessmentStatus.READY);
    }

    public static OptionsAssessment optionsAssessment(LiquidityState liquidity) {
        return optionsAssessment(START, session(SessionPhase.REGULAR, AssessmentStatus.READY),
                liquidity, AssessmentStatus.READY);
    }

    public static OptionsAssessment optionsAssessment(Instant timestamp) {
        return optionsAssessment(timestamp, session(SessionPhase.REGULAR, AssessmentStatus.READY),
                LiquidityState.LIQUID, AssessmentStatus.READY);
    }

    public static OptionsAssessment optionsAssessment(SessionPhase phase) {
        return optionsAssessment(START, session(phase, AssessmentStatus.READY),
                LiquidityState.LIQUID, AssessmentStatus.READY);
    }

    public static OptionsAssessment optionsAssessment(AssessmentStatus status) {
        return optionsAssessment(START, session(SessionPhase.REGULAR, AssessmentStatus.READY),
                LiquidityState.LIQUID, status);
    }

    public static OptionsAssessment optionsAssessment(InstrumentKey instrument,
                                                      Instant timestamp) {
        return optionsAssessment(instrument, timestamp,
                session(SessionPhase.REGULAR, AssessmentStatus.READY),
                LiquidityState.LIQUID, AssessmentStatus.READY);
    }

    public static OptionsAssessment optionsAssessment(Instant timestamp,
                                                      SessionAssessment session,
                                                      LiquidityState liquidity,
                                                      AssessmentStatus status) {
        return optionsAssessment(SPOT, timestamp, session, liquidity, status);
    }

    public static OptionsAssessment optionsAssessment(InstrumentKey instrument,
                                                      Instant timestamp,
                                                      SessionAssessment session,
                                                      LiquidityState liquidity,
                                                      AssessmentStatus status) {
        Tradability tradability = switch (liquidity) {
            case LIQUID -> Tradability.HIGH;
            case MODERATE -> Tradability.MODERATE;
            case ILLIQUID -> Tradability.LOW;
            case UNKNOWN -> Tradability.UNKNOWN;
        };
        AssessmentStatus liquidityStatus = liquidity == LiquidityState.UNKNOWN
                ? AssessmentStatus.INSUFFICIENT_DATA
                : AssessmentStatus.READY;
        LiquiditySummary liquiditySummary = new LiquiditySummary(
                liquidity == LiquidityState.UNKNOWN ? 0 : 6, 0, 0,
                Optional.empty(), liquidity, tradability, liquidityStatus, "test liquidity");
        VolatilitySummary volatility = new VolatilitySummary(Optional.of(new BigDecimal("0.25")),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                com.tradingassistant.options.domain.VolatilityTrend.FLAT,
                com.tradingassistant.options.domain.VolatilityState.STABLE,
                List.of(), List.of(), AssessmentStatus.READY, "test volatility");
        PutCallRatioSummary ratios = new PutCallRatioSummary(Optional.of(new BigDecimal("1.1")),
                Optional.empty(), List.of(), PcrAggregation.BOTH, AssessmentStatus.READY,
                "test pcr");
        MaxPainSummary maxPain = new MaxPainSummary(Optional.of(OptionsTestFixtures.EXPIRY),
                Optional.of(new BigDecimal("500")), List.of(), List.of(),
                AssessmentStatus.READY, "test max pain");
        ExpectedMoveSummary expected = new ExpectedMoveSummary(ExpectedMoveMethod.VOLATILITY,
                Optional.of(OptionsTestFixtures.EXPIRY), Optional.of(new BigDecimal("8")),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                AssessmentStatus.READY, "test expected move");
        return new OptionsAssessment(instrument, OptionsTestFixtures.UNDERLYING, timestamp,
                status, status != AssessmentStatus.READY,
                new ChainSummary(Optional.of(OptionsTestFixtures.EXPIRY), 1, 3, 6,
                        new BigDecimal("500"), AssessmentStatus.READY, "test chain"),
                volatility,
                new GreeksSummary(Optional.empty(), Optional.empty(), Greeks.zero(), 0,
                        AssessmentStatus.INSUFFICIENT_DATA, "test greeks"),
                new OpenInterestSummary(0, 0, 0, 0, List.of(), Optional.empty(),
                        Optional.empty(), List.of(), List.of(),
                        AssessmentStatus.INSUFFICIENT_DATA, "test open interest"),
                ratios, expected, liquiditySummary, maxPain, session, Map.of());
    }

    public static TradeQualificationContext context() {
        return TradeQualificationContext.of(strategyEvaluation(), marketAssessment(), null,
                candle());
    }

    public static TradeQualificationContext context(EvaluationStatus strategyStatus) {
        return TradeQualificationContext.of(strategyEvaluation(strategyStatus),
                marketAssessment(), null, candle());
    }

    public static TradeQualificationContext context(MarketAssessment market) {
        return TradeQualificationContext.of(strategyEvaluation(), market, null, candle());
    }

    public static TradeQualificationContext context(OptionsAssessment options) {
        return TradeQualificationContext.of(strategyEvaluation(), marketAssessment(), options,
                candle());
    }

    public static TradeQualificationContext context(MarketAssessment market,
                                                    OptionsAssessment options) {
        return TradeQualificationContext.of(strategyEvaluation(), market, options, candle());
    }

    public static TradeQualificationContext context(StrategyEvaluation evaluation,
                                                    MarketAssessment market,
                                                    OptionsAssessment options,
                                                    Candle candle) {
        return TradeQualificationContext.of(evaluation, market, options, candle);
    }

    public static QualificationLedger ledger() {
        return new QualificationLedger();
    }
}
