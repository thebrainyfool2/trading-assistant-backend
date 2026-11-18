package com.tradingassistant.strategy;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.domain.IndicatorStatus;
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
import com.tradingassistant.options.domain.VolatilityState;
import com.tradingassistant.options.domain.VolatilitySummary;
import com.tradingassistant.strategy.domain.StrategyEvaluationContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class StrategyTestFixtures {

    public static final InstrumentKey SPOT = OptionsTestFixtures.SPOT;
    public static final Instant START = OptionsTestFixtures.START;

    private StrategyTestFixtures() {
    }

    public static Candle candle(String close, long volume) {
        BigDecimal value = new BigDecimal(close);
        return new Candle(SPOT, Interval.ONE_MINUTE, START,
                value, value.add(BigDecimal.ONE), value.subtract(BigDecimal.ONE), value, volume);
    }

    public static IndicatorResult indicator(String type, String key, String value) {
        return new IndicatorResult(type, "1.0", START, IndicatorStatus.OK, false,
                Map.of(key, new BigDecimal(value)), Map.of());
    }

    /**
     * Market assessment with the regular descriptive defaults.
     */
    public static MarketAssessment marketAssessment() {
        return marketAssessment(
                com.tradingassistant.intelligence.domain.VolatilityState.STABLE,
                Set.of(StructureSignal.RANGE));
    }

    public static MarketAssessment marketAssessment(
            com.tradingassistant.intelligence.domain.VolatilityState volatility,
            Set<StructureSignal> signals) {
        return new MarketAssessment(SPOT, Interval.ONE_MINUTE, START,
                AssessmentStatus.READY, false,
                new Confidence(ConfidenceLevel.MEDIUM, "test assessment"),
                new TrendAssessment(TrendState.SIDEWAYS, AssessmentStatus.READY, Set.of(),
                        "test trend"),
                new MomentumAssessment(MomentumState.NEUTRAL, AssessmentStatus.READY, Set.of(),
                        "test momentum"),
                new VolatilityAssessment(volatility, AssessmentStatus.READY, Set.of(),
                        "test volatility"),
                new ParticipationAssessment(ParticipationState.AVERAGE, AssessmentStatus.READY,
                        Set.of(), "test participation"),
                new StructureAssessment(StructureBias.RANGING, signals,
                        AssessmentStatus.READY, "test structure"),
                new SessionAssessment(SessionPhase.REGULAR, AssessmentStatus.READY,
                        Optional.empty(), "test session"));
    }

    public static IndicatorResult ema(String value) {
        return indicator("EMA", "value", value);
    }

    public static Map<String, IndicatorResult> indicators(IndicatorResult... results) {
        Map<String, IndicatorResult> values = new LinkedHashMap<>();
        for (IndicatorResult result : results) {
            values.put(result.indicator(), result);
        }
        return Map.copyOf(values);
    }

    public static StrategyEvaluationContext context(String close, long volume,
                                                    IndicatorResult... indicators) {
        return new StrategyEvaluationContext(marketAssessment(), Optional.empty(),
                Optional.of(candle(close, volume)), indicators(indicators));
    }

    public static StrategyEvaluationContext context(IndicatorResult... indicators) {
        return context("105", 200_000L, indicators);
    }

    public static StrategyEvaluationContext context(MarketAssessment market,
                                                    OptionsAssessment options,
                                                    Candle candle,
                                                    IndicatorResult... indicators) {
        return new StrategyEvaluationContext(market, Optional.ofNullable(options),
                Optional.ofNullable(candle), indicators(indicators));
    }

    /**
     * Option assessment with the descriptive sections a strategy rule can read.
     */
    public static OptionsAssessment optionsAssessment(BigDecimal spot,
                                                      VolatilityState volatilityState,
                                                      BigDecimal putCallRatio,
                                                      BigDecimal maxPain,
                                                      BigDecimal expectedMove,
                                                      LiquidityState liquidityState) {
        MarketAssessment market = marketAssessment();
        AssessmentStatus volatilityStatus = volatilityState == VolatilityState.UNKNOWN
                ? AssessmentStatus.INSUFFICIENT_DATA
                : AssessmentStatus.READY;
        var trend = switch (volatilityState) {
            case EXPANDING -> com.tradingassistant.options.domain.VolatilityTrend.RISING;
            case CONTRACTING -> com.tradingassistant.options.domain.VolatilityTrend.FALLING;
            case STABLE -> com.tradingassistant.options.domain.VolatilityTrend.FLAT;
            case UNKNOWN -> com.tradingassistant.options.domain.VolatilityTrend.UNKNOWN;
        };
        VolatilitySummary volatility = new VolatilitySummary(Optional.of(new BigDecimal("0.25")),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                trend, volatilityState, List.of(), List.of(), volatilityStatus,
                "test volatility");
        PutCallRatioSummary ratios = new PutCallRatioSummary(
                Optional.ofNullable(putCallRatio), Optional.empty(), List.of(),
                PcrAggregation.BOTH,
                putCallRatio == null ? AssessmentStatus.INSUFFICIENT_DATA
                        : AssessmentStatus.READY,
                "test pcr");
        MaxPainSummary maxPainSummary = new MaxPainSummary(Optional.of(OptionsTestFixtures.EXPIRY),
                Optional.ofNullable(maxPain), List.of(), List.of(),
                maxPain == null ? AssessmentStatus.INSUFFICIENT_DATA : AssessmentStatus.READY,
                "test max pain");
        ExpectedMoveSummary expected = new ExpectedMoveSummary(ExpectedMoveMethod.VOLATILITY,
                Optional.of(OptionsTestFixtures.EXPIRY), Optional.ofNullable(expectedMove),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                expectedMove == null ? AssessmentStatus.PARTIAL : AssessmentStatus.READY,
                "test expected move");
        Tradability tradability = switch (liquidityState) {
            case LIQUID -> Tradability.HIGH;
            case MODERATE -> Tradability.MODERATE;
            case ILLIQUID -> Tradability.LOW;
            case UNKNOWN -> Tradability.UNKNOWN;
        };
        LiquiditySummary liquidity = new LiquiditySummary(
                liquidityState == LiquidityState.UNKNOWN ? 0 : 6, 0, 0,
                Optional.empty(), liquidityState, tradability,
                liquidityState == LiquidityState.UNKNOWN
                        ? AssessmentStatus.INSUFFICIENT_DATA
                        : AssessmentStatus.READY,
                "test liquidity");
        return new OptionsAssessment(SPOT, OptionsTestFixtures.UNDERLYING, START,
                AssessmentStatus.PARTIAL, true,
                new ChainSummary(Optional.of(OptionsTestFixtures.EXPIRY), 1, 3, 6, spot,
                        AssessmentStatus.READY, "test chain"),
                volatility,
                new GreeksSummary(Optional.empty(), Optional.empty(), Greeks.zero(), 0,
                        AssessmentStatus.INSUFFICIENT_DATA, "test greeks"),
                new OpenInterestSummary(0, 0, 0, 0, List.of(), Optional.empty(),
                        Optional.empty(), List.of(), List.of(),
                        AssessmentStatus.INSUFFICIENT_DATA, "test open interest"),
                ratios, expected, liquidity, maxPainSummary, market.session(), Map.of());
    }

    public static OptionsAssessment optionsAssessment() {
        return optionsAssessment(new BigDecimal("500"), VolatilityState.EXPANDING,
                new BigDecimal("1.8"), new BigDecimal("499"), new BigDecimal("8"),
                LiquidityState.LIQUID);
    }
}
