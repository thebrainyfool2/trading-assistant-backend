package com.tradingassistant.risk;

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
import com.tradingassistant.qualification.domain.QualificationRuleEvaluation;
import com.tradingassistant.qualification.domain.QualificationStatus;
import com.tradingassistant.qualification.domain.RuleStatus;
import com.tradingassistant.qualification.domain.TradeQualification;
import com.tradingassistant.risk.config.RiskEngineConfig;
import com.tradingassistant.risk.domain.RiskContext;
import com.tradingassistant.risk.domain.RiskPlan;
import com.tradingassistant.risk.domain.RiskRuleContext;
import com.tradingassistant.risk.math.RiskCalculator;
import com.tradingassistant.strategy.domain.EvaluationStatus;
import com.tradingassistant.strategy.domain.RuleEvaluation;
import com.tradingassistant.strategy.domain.StrategyEvaluation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Inputs for risk tests.
 *
 * <p>The baseline describes a fully usable assessment: a passed strategy, a
 * qualified trade, a stable market, a 5.00 ATR, a 500.00 price, a 25.00 expected
 * move and the default policy. Under those values every built-in boundary holds,
 * so a test that expects a breach varies exactly one input.</p>
 */
public final class RiskTestFixtures {

    public static final InstrumentKey SPOT = OptionsTestFixtures.SPOT;
    public static final Instant START = OptionsTestFixtures.START;
    public static final Instant LATER = START.plusSeconds(60);
    public static final String STRATEGY_ID = "EMA_TREND";
    public static final String STRATEGY_NAME = "EMA Trend";
    public static final String ATR = "5";
    public static final String PRICE = "500";
    public static final String EXPECTED_MOVE = "25";

    private RiskTestFixtures() {
    }

    public static Candle candle() {
        return candle(PRICE);
    }

    public static Candle candle(Instant timestamp) {
        BigDecimal value = new BigDecimal(PRICE);
        return new Candle(SPOT, Interval.ONE_MINUTE, timestamp,
                value, value.add(BigDecimal.ONE), value.subtract(BigDecimal.ONE), value,
                200_000L);
    }

    public static Candle candle(InstrumentKey instrument, Instant timestamp,
                                Interval interval) {
        BigDecimal value = new BigDecimal(PRICE);
        return new Candle(instrument, interval, timestamp,
                value, value.add(BigDecimal.ONE), value.subtract(BigDecimal.ONE), value,
                200_000L);
    }

    public static Candle candle(String close) {
        BigDecimal value = new BigDecimal(close);
        return new Candle(SPOT, Interval.ONE_MINUTE, START,
                value, value.add(BigDecimal.ONE), value.subtract(BigDecimal.ONE), value,
                200_000L);
    }

    public static IndicatorResult atrIndicator(String value) {
        return new IndicatorResult("ATR", "1.0", START, IndicatorStatus.OK, false,
                Map.of("value", new BigDecimal(value)), Map.of());
    }

    public static Map<String, IndicatorResult> indicators(IndicatorResult... results) {
        Map<String, IndicatorResult> values = new LinkedHashMap<>();
        for (IndicatorResult result : results) {
            values.put(result.indicator(), result);
        }
        return Map.copyOf(values);
    }

    public static MarketAssessment marketAssessment() {
        return marketAssessment(VolatilityState.STABLE);
    }

    public static MarketAssessment marketAssessment(VolatilityState volatility) {
        return marketAssessment(SPOT, START, Interval.ONE_MINUTE, volatility);
    }

    public static MarketAssessment marketAssessment(InstrumentKey instrument) {
        return marketAssessment(instrument, START, Interval.ONE_MINUTE,
                VolatilityState.STABLE);
    }

    public static MarketAssessment marketAssessment(Instant timestamp) {
        return marketAssessment(SPOT, timestamp, Interval.ONE_MINUTE,
                VolatilityState.STABLE);
    }

    public static MarketAssessment marketAssessment(Instant timestamp, Interval interval) {
        return marketAssessment(SPOT, timestamp, interval, VolatilityState.STABLE);
    }

    public static MarketAssessment marketAssessment(InstrumentKey instrument,
                                                    Instant timestamp,
                                                    Interval interval,
                                                    VolatilityState volatility) {
        return new MarketAssessment(instrument, interval, timestamp,
                AssessmentStatus.READY, false,
                new Confidence(ConfidenceLevel.HIGH, "test confidence"),
                new TrendAssessment(TrendState.SIDEWAYS, AssessmentStatus.READY, Set.of(),
                        "test trend"),
                new MomentumAssessment(MomentumState.NEUTRAL, AssessmentStatus.READY, Set.of(),
                        "test momentum"),
                new VolatilityAssessment(volatility, AssessmentStatus.READY, Set.of(),
                        "test volatility"),
                new ParticipationAssessment(ParticipationState.AVERAGE, AssessmentStatus.READY,
                        Set.of(), "test participation"),
                new StructureAssessment(StructureBias.RANGING, Set.of(StructureSignal.RANGE),
                        AssessmentStatus.READY, "test structure"),
                new SessionAssessment(SessionPhase.REGULAR, AssessmentStatus.READY,
                        Optional.empty(), "test session"));
    }

    public static StrategyEvaluation strategyEvaluation() {
        return strategyEvaluation(EvaluationStatus.PASSED);
    }

    public static StrategyEvaluation strategyEvaluation(String strategyId,
                                                       EvaluationStatus status) {
        return new StrategyEvaluation(strategyId, strategyId, START, SPOT,
                Interval.ONE_MINUTE, status,
                List.of(new RuleEvaluation("rule", status, "test rule", Map.of())),
                "1 of 1 rules passed");
    }

    public static StrategyEvaluation strategyEvaluation(EvaluationStatus status) {
        return new StrategyEvaluation(STRATEGY_ID, STRATEGY_NAME, START, SPOT,
                Interval.ONE_MINUTE, status,
                List.of(new RuleEvaluation("EMA.closeAbove", status,
                        "test rule", Map.of())),
                "1 of 1 rules passed");
    }

    public static TradeQualification qualification() {
        return qualification(QualificationStatus.QUALIFIED);
    }

    public static TradeQualification qualification(String strategyId,
                                                   QualificationStatus status) {
        return new TradeQualification(strategyId, strategyId, SPOT, Interval.ONE_MINUTE,
                START, status,
                List.of(new QualificationRuleEvaluation("SESSION_OPEN", RuleStatus.PASSED,
                        "session is open", Map.of())),
                "1 of 1 rules passed");
    }

    public static TradeQualification qualification(QualificationStatus status) {
        return new TradeQualification(STRATEGY_ID, STRATEGY_NAME, SPOT, Interval.ONE_MINUTE,
                START, status,
                List.of(new QualificationRuleEvaluation("SESSION_OPEN", RuleStatus.PASSED,
                        "session is open", Map.of())),
                "1 of 1 rules passed");
    }

    public static OptionsAssessment optionsAssessment() {
        return optionsAssessment(START, EXPECTED_MOVE);
    }

    public static OptionsAssessment optionsAssessment(Instant timestamp) {
        return optionsAssessment(timestamp, EXPECTED_MOVE);
    }

    public static OptionsAssessment optionsAssessment(BigDecimal expiryMove) {
        return optionsAssessment(START, expiryMove);
    }

    public static OptionsAssessment optionsAssessment(Instant timestamp,
                                                      String expiryMove) {
        return optionsAssessment(timestamp, new BigDecimal(expiryMove));
    }

    public static OptionsAssessment optionsAssessment(InstrumentKey instrument,
                                                      Instant timestamp) {
        return optionsAssessment(instrument, timestamp, new BigDecimal(EXPECTED_MOVE));
    }

    public static OptionsAssessment optionsAssessment(Instant timestamp,
                                                      BigDecimal expiryMove) {
        return optionsAssessment(SPOT, timestamp, expiryMove);
    }

    public static OptionsAssessment optionsAssessment(InstrumentKey instrument,
                                                      Instant timestamp,
                                                      BigDecimal expiryMove) {
        SessionAssessment session = session();
        LiquiditySummary liquidity = new LiquiditySummary(6, 0, 0, Optional.empty(),
                LiquidityState.LIQUID, Tradability.HIGH, AssessmentStatus.READY,
                "test liquidity");
        VolatilitySummary volatility = new VolatilitySummary(Optional.of(new BigDecimal("0.25")),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                com.tradingassistant.options.domain.VolatilityTrend.FLAT,
                com.tradingassistant.options.domain.VolatilityState.STABLE,
                List.of(), List.of(), AssessmentStatus.READY, "test volatility");
        PutCallRatioSummary ratios = new PutCallRatioSummary(Optional.of(new BigDecimal("1.1")),
                Optional.empty(), List.of(), PcrAggregation.BOTH, AssessmentStatus.READY,
                "test pcr");
        MaxPainSummary maxPain = new MaxPainSummary(Optional.of(OptionsTestFixtures.EXPIRY),
                Optional.of(new BigDecimal(PRICE)), List.of(), List.of(),
                AssessmentStatus.READY, "test max pain");
        ExpectedMoveSummary expected = new ExpectedMoveSummary(ExpectedMoveMethod.VOLATILITY,
                Optional.of(OptionsTestFixtures.EXPIRY), Optional.of(expiryMove),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                AssessmentStatus.READY, "test expected move");
        return new OptionsAssessment(instrument, OptionsTestFixtures.UNDERLYING, timestamp,
                AssessmentStatus.READY, false,
                new ChainSummary(Optional.of(OptionsTestFixtures.EXPIRY), 1, 3, 6,
                        new BigDecimal(PRICE), AssessmentStatus.READY, "test chain"),
                volatility,
                new GreeksSummary(Optional.empty(), Optional.empty(), Greeks.zero(), 0,
                        AssessmentStatus.INSUFFICIENT_DATA, "test greeks"),
                new OpenInterestSummary(0, 0, 0, 0, List.of(), Optional.empty(),
                        Optional.empty(), List.of(), List.of(),
                        AssessmentStatus.INSUFFICIENT_DATA, "test open interest"),
                ratios, expected, liquidity, maxPain, session, Map.of());
    }

    private static SessionAssessment session() {
        return new SessionAssessment(SessionPhase.REGULAR, AssessmentStatus.READY,
                Optional.empty(), "test session");
    }

    public static RiskContext context() {
        return new RiskContext(strategyEvaluation(), qualification(), marketAssessment(),
                Optional.of(optionsAssessment()), Optional.of(candle()),
                indicators(atrIndicator(ATR)));
    }

    public static RiskContext context(QualificationStatus status) {
        return new RiskContext(strategyEvaluation(), qualification(status), marketAssessment(),
                Optional.of(optionsAssessment()), Optional.of(candle()),
                indicators(atrIndicator(ATR)));
    }

    public static RiskContext context(VolatilityState volatility) {
        return new RiskContext(strategyEvaluation(), qualification(),
                marketAssessment(volatility), Optional.of(optionsAssessment()),
                Optional.of(candle()), indicators(atrIndicator(ATR)));
    }

    public static RiskContext context(OptionsAssessment options) {
        return new RiskContext(strategyEvaluation(), qualification(), marketAssessment(),
                Optional.ofNullable(options), Optional.of(candle()),
                indicators(atrIndicator(ATR)));
    }

    public static RiskContext contextWithoutOptions() {
        return new RiskContext(strategyEvaluation(), qualification(), marketAssessment(),
                Optional.empty(), Optional.of(candle()), indicators(atrIndicator(ATR)));
    }

    public static RiskContext contextWithoutAtr() {
        return new RiskContext(strategyEvaluation(), qualification(), marketAssessment(),
                Optional.of(optionsAssessment()), Optional.of(candle()), Map.of());
    }

    public static RiskContext contextWithAtr(String value) {
        return new RiskContext(strategyEvaluation(), qualification(), marketAssessment(),
                Optional.of(optionsAssessment()), Optional.of(candle()),
                indicators(atrIndicator(value)));
    }

    public static RiskContext contextWithEvaluation(StrategyEvaluation evaluation) {
        return new RiskContext(evaluation, qualification(), marketAssessment(),
                Optional.of(optionsAssessment()), Optional.of(candle()),
                indicators(atrIndicator(ATR)));
    }

    public static RiskPlan plan() {
        return new RiskCalculator().calculate(context(), RiskEngineConfig.DEFAULT);
    }

    public static RiskPlan plan(RiskContext context) {
        return new RiskCalculator().calculate(context, RiskEngineConfig.DEFAULT);
    }

    public static RiskPlan plan(RiskContext context, RiskEngineConfig config) {
        return new RiskCalculator().calculate(context, config);
    }

    /**
     * Plan with hand-set values, for rules whose breach cannot be produced through
     * the calculator because the calculator follows the same policy.
     */
    public static RiskPlan planWithRiskPercent(String riskPercent) {
        RiskPlan base = plan();
        return new RiskPlan(base.capital(), new BigDecimal(riskPercent), base.riskAmount(),
                base.atr(), base.volatilityAdjustment(), base.atrMultiple(), base.stop(),
                base.sizing(), base.availableReward(), base.fractionalApplied());
    }

    public static RiskRuleContext ruleContext() {
        return new RiskRuleContext(context(), plan(), RiskEngineConfig.DEFAULT);
    }

    public static RiskRuleContext ruleContext(RiskContext context, RiskEngineConfig config) {
        return new RiskRuleContext(context, plan(context, config), config);
    }

    public static RiskRuleContext ruleContext(RiskPlan plan, RiskEngineConfig config) {
        return new RiskRuleContext(context(), plan, config);
    }

    /**
     * Plan whose stop values are set by hand, for boundaries the calculator cannot
     * breach while following the same policy.
     */
    public static RiskPlan planWithStop(String atr, String distance, String multiplier) {
        RiskPlan base = plan();
        var stop = new com.tradingassistant.risk.domain.StopCalculation(
                com.tradingassistant.risk.domain.StopMethodology.ATR, new BigDecimal(atr),
                new BigDecimal(multiplier), base.volatilityAdjustment(),
                new BigDecimal(distance), base.stop().distancePercent());
        return new RiskPlan(base.capital(), base.riskPercent(), base.riskAmount(),
                Optional.of(new BigDecimal(atr)), base.volatilityAdjustment(),
                new BigDecimal(multiplier), stop, base.sizing(), base.availableReward(),
                base.fractionalApplied());
    }

    /**
     * Plan whose reported ATR is set by hand, including a zero ATR.
     */
    public static RiskPlan planWithAtr(String atr) {
        RiskPlan base = plan();
        return new RiskPlan(base.capital(), base.riskPercent(), base.riskAmount(),
                Optional.of(new BigDecimal(atr)), base.volatilityAdjustment(),
                base.atrMultiple(), base.stop(), base.sizing(), base.availableReward(),
                base.fractionalApplied());
    }

    /**
     * Plan whose volatility adjustment is set by hand.
     */
    public static RiskPlan planWithAdjustment(String adjustment) {
        RiskPlan base = plan();
        return new RiskPlan(base.capital(), base.riskPercent(), base.riskAmount(), base.atr(),
                new BigDecimal(adjustment), base.atrMultiple(), base.stop(), base.sizing(),
                base.availableReward(), base.fractionalApplied());
    }
}
