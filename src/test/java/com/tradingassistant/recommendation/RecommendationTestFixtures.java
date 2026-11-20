package com.tradingassistant.recommendation;

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
import com.tradingassistant.intelligence.domain.VolatilityState;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.options.OptionsTestFixtures;
import com.tradingassistant.options.domain.ChainSnapshot;
import com.tradingassistant.options.domain.ChainSummary;
import com.tradingassistant.options.domain.ExpectedMoveMethod;
import com.tradingassistant.options.domain.ExpectedMoveSummary;
import com.tradingassistant.options.domain.Greeks;
import com.tradingassistant.options.domain.GreeksSummary;
import com.tradingassistant.options.domain.LiquidityState;
import com.tradingassistant.options.domain.LiquiditySummary;
import com.tradingassistant.options.domain.MaxPainSummary;
import com.tradingassistant.options.domain.OpenInterestBuildUp;
import com.tradingassistant.options.domain.OpenInterestSummary;
import com.tradingassistant.options.domain.OptionsAssessment;
import com.tradingassistant.options.domain.PcrAggregation;
import com.tradingassistant.options.domain.PutCallRatioSummary;
import com.tradingassistant.options.domain.StrikeOpenInterest;
import com.tradingassistant.options.domain.Tradability;
import com.tradingassistant.options.domain.VolatilitySummary;
import com.tradingassistant.options.domain.VolatilityTrend;
import com.tradingassistant.platform.readmodel.MarketDataFreshnessService.Freshness;
import com.tradingassistant.platform.readmodel.MarketDataFreshnessService.FreshnessStatus;
import com.tradingassistant.platform.readmodel.MarketDataFreshnessService.MarketSessionState;
import com.tradingassistant.qualification.domain.QualificationStatus;
import com.tradingassistant.qualification.domain.TradeQualification;
import com.tradingassistant.recommendation.config.ExpirySelection;
import com.tradingassistant.recommendation.config.RecommendationConfig;
import com.tradingassistant.recommendation.config.StrikeMode;
import com.tradingassistant.recommendation.config.StrikeSelectionPolicy;
import com.tradingassistant.recommendation.domain.RecommendationContext;
import com.tradingassistant.recommendation.domain.ScorerId;
import com.tradingassistant.risk.RiskTestFixtures;
import com.tradingassistant.risk.config.RiskEngineConfig;
import com.tradingassistant.risk.domain.RiskAssessment;
import com.tradingassistant.risk.engine.RiskEngine;
import com.tradingassistant.risk.registry.BuiltInRiskRules;
import com.tradingassistant.risk.registry.RiskRuleRegistry;
import com.tradingassistant.strategy.domain.EvaluationStatus;
import com.tradingassistant.strategy.domain.StrategyEvaluation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Inputs for recommendation tests. Everything is built from existing immutable
 * engine models; no recommendation production code is used to create fixtures.
 */
public final class RecommendationTestFixtures {

    public static final InstrumentKey SPOT = OptionsTestFixtures.SPOT;
    public static final Instant START = OptionsTestFixtures.START;
    public static final Interval INTERVAL = Interval.ONE_MINUTE;
    public static final String STRATEGY_ID = RiskTestFixtures.STRATEGY_ID;
    public static final String UNDERLYING = OptionsTestFixtures.UNDERLYING;

    private static final RiskEngine RISK_ENGINE = new RiskEngine(RiskEngineConfig.DEFAULT,
            new RiskRuleRegistry(BuiltInRiskRules.all()), Runnable::run);

    private RecommendationTestFixtures() {
    }

    public static RecommendationConfig config() {
        return config(1, BigDecimal.ZERO, BigDecimal.ONE);
    }

    public static RecommendationConfig config(int weight, BigDecimal minimumConfidence,
                                              BigDecimal minimumDirectionalEdge) {
        Map<ScorerId, BigDecimal> weights = new EnumMap<>(ScorerId.class);
        for (ScorerId id : ScorerId.values()) {
            weights.put(id, BigDecimal.valueOf(id == ScorerId.TREND
                    ? weight * 2 : weight));
        }
        return new RecommendationConfig(true, weights, minimumConfidence,
                minimumDirectionalEdge,
                new BigDecimal("1.20"), new BigDecimal("0.80"),
                new BigDecimal("80"), new BigDecimal("20"),
                Set.of(com.tradingassistant.options.domain.ExpiryKind.WEEKLY,
                        com.tradingassistant.options.domain.ExpiryKind.MONTHLY),
                ExpirySelection.NEAREST,
                new StrikeSelectionPolicy(StrikeMode.ATM, 0, 5));
    }

    public static RecommendationConfig weeklyAtmConfig() {
        return config();
    }

    public static RecommendationConfig configWithSelection(StrikeMode mode, int offset,
                                                           ExpirySelection selection) {
        Map<ScorerId, BigDecimal> weights = new EnumMap<>(ScorerId.class);
        for (ScorerId id : ScorerId.values()) {
            weights.put(id, BigDecimal.ONE);
        }
        return new RecommendationConfig(true, weights, BigDecimal.ZERO,
                BigDecimal.ONE, new BigDecimal("1.20"), new BigDecimal("0.80"),
                new BigDecimal("80"), new BigDecimal("20"),
                Set.of(com.tradingassistant.options.domain.ExpiryKind.WEEKLY,
                        com.tradingassistant.options.domain.ExpiryKind.MONTHLY),
                selection, new StrikeSelectionPolicy(mode, offset, 5));
    }

    public static RecommendationConfig configWithZeroWeight(ScorerId zero) {
        Map<ScorerId, BigDecimal> weights = new EnumMap<>(ScorerId.class);
        for (ScorerId id : ScorerId.values()) {
            weights.put(id, id == zero ? BigDecimal.ZERO : BigDecimal.ONE);
        }
        return new RecommendationConfig(true, weights, BigDecimal.ZERO,
                BigDecimal.ONE, new BigDecimal("1.20"), new BigDecimal("0.80"),
                new BigDecimal("80"), new BigDecimal("20"),
                Set.of(com.tradingassistant.options.domain.ExpiryKind.WEEKLY,
                        com.tradingassistant.options.domain.ExpiryKind.MONTHLY),
                ExpirySelection.NEAREST,
                new StrikeSelectionPolicy(StrikeMode.ATM, 0, 5));
    }

    public static MarketAssessment bullishMarket() {
        return market(TrendState.BULLISH, MomentumState.STRENGTHENING,
                StructureBias.UPTREND, ParticipationState.STRONG,
                Set.of(StructureSignal.HIGHER_HIGH, StructureSignal.HIGHER_LOW,
                        StructureSignal.BREAKOUT_CANDIDATE));
    }

    public static MarketAssessment bearishMarket() {
        return market(TrendState.BEARISH, MomentumState.WEAKENING,
                StructureBias.DOWNTREND, ParticipationState.STRONG,
                Set.of(StructureSignal.LOWER_HIGH, StructureSignal.LOWER_LOW,
                        StructureSignal.BREAKDOWN_CANDIDATE));
    }

    public static MarketAssessment neutralMarket() {
        return market(TrendState.SIDEWAYS, MomentumState.NEUTRAL,
                StructureBias.RANGING, ParticipationState.AVERAGE,
                Set.of(StructureSignal.RANGE));
    }

    public static MarketAssessment market(TrendState trend, MomentumState momentum,
                                          StructureBias structure,
                                          ParticipationState participation,
                                          Set<StructureSignal> signals) {
        return new MarketAssessment(SPOT, INTERVAL, START, AssessmentStatus.READY, false,
                new Confidence(ConfidenceLevel.HIGH, "test confidence"),
                new TrendAssessment(trend, AssessmentStatus.READY,
                        Set.of("EMA", "ADX"), "trend description"),
                new MomentumAssessment(momentum, AssessmentStatus.READY,
                        Set.of("RSI", "MACD"), "momentum description"),
                new VolatilityAssessment(VolatilityState.STABLE, AssessmentStatus.READY,
                        Set.of("ATR"), "volatility description"),
                new ParticipationAssessment(participation, AssessmentStatus.READY,
                        Set.of("OBV"), "participation description"),
                new StructureAssessment(structure, signals, AssessmentStatus.READY,
                        "structure description"),
                new SessionAssessment(SessionPhase.REGULAR, AssessmentStatus.READY,
                        Optional.empty(), "session is open"));
    }

    public static OptionsAssessment bullishOptions() {
        return options(new BigDecimal("1.30"), new BigDecimal("0.80"),
                new BigDecimal("15"), OpenInterestBuildUp.LONG_BUILD_UP,
                OpenInterestBuildUp.LONG_UNWINDING);
    }

    public static OptionsAssessment bearishOptions() {
        return options(new BigDecimal("0.65"), new BigDecimal("-0.80"),
                new BigDecimal("15"), OpenInterestBuildUp.SHORT_BUILD_UP,
                OpenInterestBuildUp.LONG_BUILD_UP);
    }

    public static OptionsAssessment neutralOptions() {
        return options(new BigDecimal("1.00"), BigDecimal.ZERO,
                new BigDecimal("50"), OpenInterestBuildUp.NEUTRAL,
                OpenInterestBuildUp.NEUTRAL);
    }

    public static OptionsAssessment options(BigDecimal pcr, BigDecimal netDelta,
                                            BigDecimal ivRank,
                                            OpenInterestBuildUp callBuildUp,
                                            OpenInterestBuildUp putBuildUp) {
        LiquiditySummary liquidity = new LiquiditySummary(6, 0, 0,
                Optional.of(new BigDecimal("0.10")), LiquidityState.LIQUID,
                Tradability.HIGH, AssessmentStatus.READY, "quoted market is liquid");
        VolatilitySummary volatility = new VolatilitySummary(
                Optional.of(new BigDecimal("0.22")), Optional.empty(),
                Optional.of(ivRank), Optional.of(ivRank), Optional.empty(),
                VolatilityTrend.FLAT,
                com.tradingassistant.options.domain.VolatilityState.STABLE,
                List.of(), List.of(), AssessmentStatus.READY, "volatility is stable");
        PutCallRatioSummary ratios = new PutCallRatioSummary(Optional.of(pcr),
                Optional.empty(), List.of(), PcrAggregation.BOTH,
                AssessmentStatus.READY, "put/call ratio is available");
        MaxPainSummary maxPain = new MaxPainSummary(Optional.of(OptionsTestFixtures.EXPIRY),
                Optional.of(new BigDecimal("500")), List.of(), List.of(),
                AssessmentStatus.READY, "max pain is available");
        ExpectedMoveSummary expected = new ExpectedMoveSummary(
                ExpectedMoveMethod.VOLATILITY, Optional.of(OptionsTestFixtures.EXPIRY),
                Optional.of(new BigDecimal("25")), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), AssessmentStatus.READY,
                "expected move is available");
        OpenInterestSummary openInterest = new OpenInterestSummary(1_200, 800,
                500, 400,
                List.of(new StrikeOpenInterest(OptionsTestFixtures.EXPIRY,
                        new BigDecimal("500"), 1_200, 800,
                        Optional.empty(), Optional.empty(), 500, 400,
                        Optional.empty(), Optional.empty(), callBuildUp, putBuildUp)),
                Optional.of(new BigDecimal("500")), Optional.of(new BigDecimal("500")),
                List.of(new BigDecimal("480"), new BigDecimal("490")),
                List.of(new BigDecimal("520"), new BigDecimal("530")),
                AssessmentStatus.READY, "open interest is available");
        GreeksSummary greeks = new GreeksSummary(
                Optional.of(greeks(new BigDecimal("0.55"))),
                Optional.of(greeks(new BigDecimal("-0.45"))),
                greeks(netDelta), 2, AssessmentStatus.READY, "greeks are available");
        return new OptionsAssessment(SPOT, UNDERLYING, START, AssessmentStatus.READY,
                false,
                new ChainSummary(Optional.of(OptionsTestFixtures.EXPIRY), 1, 9, 18,
                        new BigDecimal("500"), AssessmentStatus.READY, "chain is ready"),
                volatility, greeks, openInterest, ratios, expected, liquidity, maxPain,
                new SessionAssessment(SessionPhase.REGULAR, AssessmentStatus.READY,
                        Optional.empty(), "session is open"),
                Map.of());
    }

    private static Greeks greeks(BigDecimal delta) {
        return new Greeks(delta, new BigDecimal("0.02"), new BigDecimal("-1"),
                new BigDecimal("0.10"), new BigDecimal("0.01"));
    }

    public static ChainSnapshot chain() {
        return OptionsTestFixtures.defaultSnapshot();
    }

    public static Map<String, IndicatorResult> indicators() {
        Map<String, IndicatorResult> values = new LinkedHashMap<>();
        values.put("EMA", indicator("EMA", Map.of("value", "510")));
        values.put("ADX", indicator("ADX", Map.of("adx", "30")));
        values.put("SUPERTREND", indicator("SUPERTREND",
                Map.of("supertrend", "495", "direction", "1")));
        values.put("DONCHIAN", indicator("DONCHIAN",
                Map.of("upper", "520", "lower", "480", "middle", "500")));
        values.put("RSI", indicator("RSI", Map.of("value", "63")));
        return Map.copyOf(values);
    }

    private static IndicatorResult indicator(String type, Map<String, String> values) {
        Map<String, BigDecimal> numbers = new LinkedHashMap<>();
        values.forEach((key, value) -> numbers.put(key, new BigDecimal(value)));
        return new IndicatorResult(type, "test", START, IndicatorStatus.OK,
                false, numbers, Map.of());
    }

    public static Candle candle() {
        return new Candle(SPOT, INTERVAL, START, new BigDecimal("505"),
                new BigDecimal("510"), new BigDecimal("500"), new BigDecimal("505"),
                200_000L);
    }

    public static StrategyEvaluation strategy() {
        return new StrategyEvaluation(STRATEGY_ID, STRATEGY_ID, START, SPOT, INTERVAL,
                EvaluationStatus.PASSED,
                List.of(new com.tradingassistant.strategy.domain.RuleEvaluation(
                        "trend", EvaluationStatus.PASSED, "trend passed", Map.of())),
                "strategy passed");
    }

    public static TradeQualification qualification() {
        return RiskTestFixtures.qualification();
    }

    public static TradeQualification disqualified() {
        return RiskTestFixtures.qualification(QualificationStatus.DISQUALIFIED);
    }

    public static RiskAssessment risk() {
        return RISK_ENGINE.evaluate(RiskTestFixtures.context());
    }

    public static Freshness freshness() {
        return new Freshness(SPOT, FreshnessStatus.FRESH, START, START, INTERVAL,
                5L, 5L, new BigDecimal("500"), MarketSessionState.OPEN);
    }

    public static Freshness staleFreshness() {
        return new Freshness(SPOT, FreshnessStatus.STALE, START.minusSeconds(600),
                START.minusSeconds(600), INTERVAL, 600L, 600L,
                new BigDecimal("500"), MarketSessionState.OPEN);
    }

    public static RecommendationContext bullishContext() {
        return context(bullishMarket(), bullishOptions(), qualification(), risk(),
                freshness());
    }

    public static RecommendationContext bearishContext() {
        return context(bearishMarket(), bearishOptions(), qualification(), risk(),
                freshness());
    }

    public static RecommendationContext neutralContext() {
        return context(neutralMarket(), neutralOptions(), qualification(), risk(),
                freshness());
    }

    public static RecommendationContext context(MarketAssessment market,
                                                OptionsAssessment options,
                                                TradeQualification qualification,
                                                RiskAssessment risk,
                                                Freshness freshness) {
        return new RecommendationContext(market, Optional.of(options),
                Optional.of(chain()), indicators(), Optional.of(candle()),
                List.of(strategy()), List.of(qualification), List.of(risk), freshness);
    }
}
