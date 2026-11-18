package com.tradingassistant.strategy.registry;

import com.tradingassistant.intelligence.domain.StructureSignal;
import com.tradingassistant.intelligence.domain.VolatilityState;
import com.tradingassistant.options.domain.LiquidityState;
import com.tradingassistant.strategy.StrategyTestFixtures;
import com.tradingassistant.strategy.config.StrategyEngineConfig;
import com.tradingassistant.strategy.domain.EvaluationStatus;
import com.tradingassistant.strategy.domain.StrategyDefinition;
import com.tradingassistant.strategy.domain.StrategyEvaluation;
import com.tradingassistant.strategy.domain.StrategyEvaluationContext;
import com.tradingassistant.strategy.engine.StrategyEvaluationEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class BuiltInStrategiesTest {

    private final StrategyEvaluationEngine engine = new StrategyEvaluationEngine(
            StrategyEngineConfig.DEFAULT, Runnable::run);

    private StrategyEvaluation evaluate(StrategyDefinition definition,
                                        StrategyEvaluationContext context) {
        return engine.evaluate(context, List.of(definition)).get(0);
    }

    private StrategyEvaluationContext marketOnly(VolatilityState volatility,
                                                 Set<StructureSignal> signals) {
        return StrategyEvaluationContext.of(
                StrategyTestFixtures.marketAssessment(volatility, signals));
    }

    private StrategyEvaluationContext optionsContext(String volatility,
                                                     String pcr, String maxPain,
                                                     String move, LiquidityState liquidity) {
        return StrategyTestFixtures.context(StrategyTestFixtures.marketAssessment(),
                StrategyTestFixtures.optionsAssessment(new BigDecimal("500"),
                        com.tradingassistant.options.domain.VolatilityState.valueOf(volatility),
                        pcr == null ? null : new BigDecimal(pcr),
                        maxPain == null ? null : new BigDecimal(maxPain),
                        move == null ? null : new BigDecimal(move),
                        liquidity),
                null);
    }

    @Test
    void exposesFifteenDistinctEnabledDefinitions() {
        List<StrategyDefinition> definitions = BuiltInStrategies.all();

        assertThat(definitions).hasSize(15);
        assertThat(definitions).extracting(StrategyDefinition::id)
                .doesNotHaveDuplicates();
        assertThat(definitions).allSatisfy(definition -> {
            assertThat(definition.enabled()).isTrue();
            assertThat(definition.ruleCount()).isPositive();
            assertThat(definition.requiredInputs()).isNotEmpty();
        });
        assertThat(definitions.stream().map(StrategyDefinition::id).collect(Collectors.toSet()))
                .contains("EMA_TREND", "EMA_ADX", "SUPERTREND", "VWAP_TREND",
                        "RSI_PULLBACK", "MACD_MOMENTUM", "BOLLINGER_EXPANSION", "BREAKOUT",
                        "BREAKDOWN", "HIGH_VOLUME", "OPTION_IV_EXPANSION", "PCR_EXTREME",
                        "MAX_PAIN_PROXIMITY", "EXPECTED_MOVE", "LIQUIDITY_FILTER");
    }

    @Test
    void emaTrendPassesWhenCloseIsAboveTheEmaAndFailsOtherwise() {
        StrategyDefinition strategy = BuiltInStrategies.emaTrend();

        assertThat(evaluate(strategy, StrategyTestFixtures.context("105", 1_000L,
                StrategyTestFixtures.ema("100"))).status())
                .isEqualTo(EvaluationStatus.PASSED);
        assertThat(evaluate(strategy, StrategyTestFixtures.context("95", 1_000L,
                StrategyTestFixtures.ema("100"))).status())
                .isEqualTo(EvaluationStatus.FAILED);
    }

    @Test
    void emaAdxRequiresBothTheEmaConditionAndAdxStrength() {
        StrategyDefinition strategy = BuiltInStrategies.emaAdx();
        var strong = StrategyTestFixtures.indicators(StrategyTestFixtures.ema("100"),
                StrategyTestFixtures.indicator("ADX", "adx", "25"));
        var weak = StrategyTestFixtures.indicators(StrategyTestFixtures.ema("100"),
                StrategyTestFixtures.indicator("ADX", "adx", "12"));

        assertThat(evaluate(strategy, StrategyTestFixtures.context("105", 1_000L,
                strong.get("EMA"), strong.get("ADX"))).status())
                .isEqualTo(EvaluationStatus.PASSED);
        assertThat(evaluate(strategy, StrategyTestFixtures.context("105", 1_000L,
                weak.get("EMA"), weak.get("ADX"))).status())
                .isEqualTo(EvaluationStatus.FAILED);
    }

    @Test
    void supertrendPassesOnAPositiveDirection() {
        StrategyDefinition strategy = BuiltInStrategies.supertrend();

        assertThat(evaluate(strategy, StrategyTestFixtures.context(
                StrategyTestFixtures.indicator("SUPERTREND", "direction", "1"))).status())
                .isEqualTo(EvaluationStatus.PASSED);
        assertThat(evaluate(strategy, StrategyTestFixtures.context(
                StrategyTestFixtures.indicator("SUPERTREND", "direction", "-1"))).status())
                .isEqualTo(EvaluationStatus.FAILED);
    }

    @Test
    void vwapTrendPassesWhenCloseIsAboveTheVwap() {
        StrategyDefinition strategy = BuiltInStrategies.vwapTrend();

        assertThat(evaluate(strategy, StrategyTestFixtures.context("105", 1_000L,
                StrategyTestFixtures.indicator("VWAP", "value", "100"))).status())
                .isEqualTo(EvaluationStatus.PASSED);
        assertThat(evaluate(strategy, StrategyTestFixtures.context("95", 1_000L,
                StrategyTestFixtures.indicator("VWAP", "value", "100"))).status())
                .isEqualTo(EvaluationStatus.FAILED);
    }

    @Test
    void rsiPullbackPassesInsideThePullbackBandOnly() {
        StrategyDefinition strategy = BuiltInStrategies.rsiPullback();

        assertThat(evaluate(strategy, StrategyTestFixtures.context(
                StrategyTestFixtures.indicator("RSI", "value", "38"))).status())
                .isEqualTo(EvaluationStatus.PASSED);
        assertThat(evaluate(strategy, StrategyTestFixtures.context(
                StrategyTestFixtures.indicator("RSI", "value", "62"))).status())
                .isEqualTo(EvaluationStatus.FAILED);
    }

    @Test
    void macdMomentumPassesForAPositiveHistogram() {
        StrategyDefinition strategy = BuiltInStrategies.macdMomentum();

        assertThat(evaluate(strategy, StrategyTestFixtures.context(
                StrategyTestFixtures.indicator("MACD", "histogram", "1.2"))).status())
                .isEqualTo(EvaluationStatus.PASSED);
        assertThat(evaluate(strategy, StrategyTestFixtures.context(
                StrategyTestFixtures.indicator("MACD", "histogram", "-1.2"))).status())
                .isEqualTo(EvaluationStatus.FAILED);
    }

    @Test
    void bollingerExpansionPassesForAnExpandingMarketVolatility() {
        StrategyDefinition strategy = BuiltInStrategies.bollingerExpansion();

        assertThat(evaluate(strategy, marketOnly(VolatilityState.EXPANDING, Set.of())).status())
                .isEqualTo(EvaluationStatus.PASSED);
        assertThat(evaluate(strategy, marketOnly(VolatilityState.STABLE, Set.of())).status())
                .isEqualTo(EvaluationStatus.FAILED);
    }

    @Test
    void breakoutAndBreakdownReadTheStructureSignals() {
        StrategyEvaluationContext breakout = marketOnly(VolatilityState.STABLE,
                Set.of(StructureSignal.BREAKOUT_CANDIDATE));
        StrategyEvaluationContext breakdown = marketOnly(VolatilityState.STABLE,
                Set.of(StructureSignal.BREAKDOWN_CANDIDATE));

        assertThat(evaluate(BuiltInStrategies.breakout(), breakout).status())
                .isEqualTo(EvaluationStatus.PASSED);
        assertThat(evaluate(BuiltInStrategies.breakout(), breakdown).status())
                .isEqualTo(EvaluationStatus.FAILED);
        assertThat(evaluate(BuiltInStrategies.breakdown(), breakdown).status())
                .isEqualTo(EvaluationStatus.PASSED);
        assertThat(evaluate(BuiltInStrategies.breakdown(), breakout).status())
                .isEqualTo(EvaluationStatus.FAILED);
    }

    @Test
    void highVolumePassesOnlyAboveTheConfiguredMinimum() {
        StrategyDefinition strategy = BuiltInStrategies.highVolume();

        assertThat(evaluate(strategy, StrategyTestFixtures.context("105", 200_000L)).status())
                .isEqualTo(EvaluationStatus.PASSED);
        assertThat(evaluate(strategy, StrategyTestFixtures.context("105", 10L)).status())
                .isEqualTo(EvaluationStatus.FAILED);
    }

    @Test
    void optionIvExpansionReadsTheOptionsVolatilityState() {
        StrategyDefinition strategy = BuiltInStrategies.optionIvExpansion();

        assertThat(evaluate(strategy, optionsContext("EXPANDING", "1.0", "500", "8",
                LiquidityState.LIQUID)).status()).isEqualTo(EvaluationStatus.PASSED);
        assertThat(evaluate(strategy, optionsContext("STABLE", "1.0", "500", "8",
                LiquidityState.LIQUID)).status()).isEqualTo(EvaluationStatus.FAILED);
    }

    @Test
    void pcrExtremeRequiresTheConfiguredRatio() {
        StrategyDefinition strategy = BuiltInStrategies.pcrExtreme();

        assertThat(evaluate(strategy, optionsContext("STABLE", "1.8", "500", "8",
                LiquidityState.LIQUID)).status()).isEqualTo(EvaluationStatus.PASSED);
        assertThat(evaluate(strategy, optionsContext("STABLE", "0.9", "500", "8",
                LiquidityState.LIQUID)).status()).isEqualTo(EvaluationStatus.FAILED);
    }

    @Test
    void maxPainProximityRequiresTheConfiguredDistance() {
        StrategyDefinition strategy = BuiltInStrategies.maxPainProximity();

        assertThat(evaluate(strategy, optionsContext("STABLE", "1.0", "503", "8",
                LiquidityState.LIQUID)).status()).isEqualTo(EvaluationStatus.PASSED);
        assertThat(evaluate(strategy, optionsContext("STABLE", "1.0", "550", "8",
                LiquidityState.LIQUID)).status()).isEqualTo(EvaluationStatus.FAILED);
    }

    @Test
    void expectedMoveRequiresTheConfiguredShareOfSpot() {
        StrategyDefinition strategy = BuiltInStrategies.expectedMove();

        assertThat(evaluate(strategy, optionsContext("STABLE", "1.0", "500", "8",
                LiquidityState.LIQUID)).status()).isEqualTo(EvaluationStatus.PASSED);
        assertThat(evaluate(strategy, optionsContext("STABLE", "1.0", "500", "2",
                LiquidityState.LIQUID)).status()).isEqualTo(EvaluationStatus.FAILED);
    }

    @Test
    void liquidityFilterRequiresALiquidChain() {
        StrategyDefinition strategy = BuiltInStrategies.liquidityFilter();

        assertThat(evaluate(strategy, optionsContext("STABLE", "1.0", "500", "8",
                LiquidityState.LIQUID)).status()).isEqualTo(EvaluationStatus.PASSED);
        assertThat(evaluate(strategy, optionsContext("STABLE", "1.0", "500", "8",
                LiquidityState.MODERATE)).status()).isEqualTo(EvaluationStatus.FAILED);
    }

    @Test
    void strategiesWithoutTheirInputsReportInsufficientData() {
        StrategyDefinition strategy = BuiltInStrategies.optionIvExpansion();

        assertThat(evaluate(strategy, StrategyTestFixtures.context("105", 1_000L)).status())
                .isEqualTo(EvaluationStatus.INSUFFICIENT_DATA);
    }

    @Test
    void everyBuiltInDefinitionCanBeEvaluatedAgainstItsOwnFixture() {
        StrategyRegistry registry = new StrategyRegistry(StrategyEngineConfig.DEFAULT,
                BuiltInStrategies.all());

        List<StrategyEvaluation> evaluations = engine.evaluate(
                StrategyTestFixtures.context("105", 200_000L,
                        StrategyTestFixtures.ema("100"),
                        StrategyTestFixtures.indicator("ADX", "adx", "25"),
                        StrategyTestFixtures.indicator("SUPERTREND", "direction", "1"),
                        StrategyTestFixtures.indicator("VWAP", "value", "100"),
                        StrategyTestFixtures.indicator("RSI", "value", "38"),
                        StrategyTestFixtures.indicator("MACD", "histogram", "1")),
                registry.enabled());

        assertThat(evaluations).hasSize(15);
        assertThat(evaluations).filteredOn(evaluation -> evaluation.strategyId()
                        .equals("EMA_TREND"))
                .singleElement()
                .satisfies(evaluation -> assertThat(evaluation.status())
                        .isEqualTo(EvaluationStatus.PASSED));
        assertThat(evaluations).filteredOn(evaluation -> evaluation.strategyId()
                        .equals("PCR_EXTREME"))
                .singleElement()
                .satisfies(evaluation -> assertThat(evaluation.status())
                        .isEqualTo(EvaluationStatus.INSUFFICIENT_DATA));
    }
}
