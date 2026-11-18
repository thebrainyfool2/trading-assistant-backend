package com.tradingassistant.strategy.rule;

import com.tradingassistant.options.domain.LiquidityState;
import com.tradingassistant.options.domain.VolatilityState;
import com.tradingassistant.strategy.StrategyTestFixtures;
import com.tradingassistant.strategy.domain.EvaluationStatus;
import com.tradingassistant.strategy.domain.RuleEvaluation;
import com.tradingassistant.strategy.domain.StrategyEvaluationContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptionsRulesTest {

    private StrategyEvaluationContext context(BigDecimal spot, VolatilityState volatility,
                                              BigDecimal pcr, BigDecimal maxPain,
                                              BigDecimal move, LiquidityState liquidity) {
        return StrategyTestFixtures.context(StrategyTestFixtures.marketAssessment(),
                StrategyTestFixtures.optionsAssessment(spot, volatility, pcr, maxPain, move,
                        liquidity),
                null);
    }

    @Test
    void ivExpansionPassesOnlyWhenVolatilityExpands() {
        var rule = new OptionsRules.IvExpansion("iv.expanding");

        RuleEvaluation expanding = rule.evaluate(context(new BigDecimal("500"),
                VolatilityState.EXPANDING, new BigDecimal("1.2"), new BigDecimal("500"),
                new BigDecimal("8"), LiquidityState.LIQUID));
        RuleEvaluation stable = rule.evaluate(context(new BigDecimal("500"),
                VolatilityState.STABLE, new BigDecimal("1.2"), new BigDecimal("500"),
                new BigDecimal("8"), LiquidityState.LIQUID));

        assertThat(expanding.status()).isEqualTo(EvaluationStatus.PASSED);
        assertThat(stable.status()).isEqualTo(EvaluationStatus.FAILED);
    }

    @Test
    void ivExpansionReportsInsufficientDataWithoutAnOptionsAssessment() {
        var rule = new OptionsRules.IvExpansion("iv.expanding");

        RuleEvaluation evaluation = rule.evaluate(StrategyEvaluationContext.of(
                StrategyTestFixtures.marketAssessment()));

        assertThat(evaluation.status()).isEqualTo(EvaluationStatus.INSUFFICIENT_DATA);
        assertThat(evaluation.reason()).contains("options assessment");
    }

    @Test
    void ivExpansionReportsUnknownVolatilityAsInsufficient() {
        var rule = new OptionsRules.IvExpansion("iv.expanding");

        RuleEvaluation evaluation = rule.evaluate(context(new BigDecimal("500"),
                VolatilityState.UNKNOWN, null, null, null, LiquidityState.UNKNOWN));

        assertThat(evaluation.status()).isEqualTo(EvaluationStatus.INSUFFICIENT_DATA);
    }

    @Test
    void pcrRuleComparesTheChainRatio() {
        var rule = new OptionsRules.PcrAtLeast("pcr.at-least", new BigDecimal("1.5"));

        assertThat(rule.evaluate(context(new BigDecimal("500"), VolatilityState.STABLE,
                new BigDecimal("1.8"), null, null, LiquidityState.LIQUID)).status())
                .isEqualTo(EvaluationStatus.PASSED);
        assertThat(rule.evaluate(context(new BigDecimal("500"), VolatilityState.STABLE,
                new BigDecimal("0.9"), null, null, LiquidityState.LIQUID)).status())
                .isEqualTo(EvaluationStatus.FAILED);
        assertThat(rule.evaluate(context(new BigDecimal("500"), VolatilityState.STABLE,
                null, null, null, LiquidityState.LIQUID)).status())
                .isEqualTo(EvaluationStatus.INSUFFICIENT_DATA);
    }

    @Test
    void maxPainRuleMeasuresDistanceFromSpot() {
        var rule = new OptionsRules.MaxPainWithinPercent("maxpain.within",
                new BigDecimal("2"));

        assertThat(rule.evaluate(context(new BigDecimal("500"), VolatilityState.STABLE,
                null, new BigDecimal("503"), null, LiquidityState.LIQUID)).status())
                .isEqualTo(EvaluationStatus.PASSED);
        assertThat(rule.evaluate(context(new BigDecimal("500"), VolatilityState.STABLE,
                null, new BigDecimal("550"), null, LiquidityState.LIQUID)).status())
                .isEqualTo(EvaluationStatus.FAILED);
        assertThat(rule.evaluate(context(new BigDecimal("500"), VolatilityState.STABLE,
                null, null, null, LiquidityState.LIQUID)).status())
                .isEqualTo(EvaluationStatus.INSUFFICIENT_DATA);
    }

    @Test
    void expectedMoveRuleComparesTheShareOfSpot() {
        var rule = new OptionsRules.ExpectedMoveAtLeastPercent("expected-move.at-least",
                new BigDecimal("1"));

        assertThat(rule.evaluate(context(new BigDecimal("500"), VolatilityState.STABLE,
                null, null, new BigDecimal("8"), LiquidityState.LIQUID)).status())
                .isEqualTo(EvaluationStatus.PASSED);
        assertThat(rule.evaluate(context(new BigDecimal("500"), VolatilityState.STABLE,
                null, null, new BigDecimal("2"), LiquidityState.LIQUID)).status())
                .isEqualTo(EvaluationStatus.FAILED);
        assertThat(rule.evaluate(context(new BigDecimal("500"), VolatilityState.STABLE,
                null, null, null, LiquidityState.LIQUID)).status())
                .isEqualTo(EvaluationStatus.INSUFFICIENT_DATA);
    }

    @Test
    void liquidityRuleRequiresALiquidChain() {
        var rule = new OptionsRules.LiquidityAtLeast("liquidity.liquid");

        assertThat(rule.evaluate(context(new BigDecimal("500"), VolatilityState.STABLE,
                null, null, null, LiquidityState.LIQUID)).status())
                .isEqualTo(EvaluationStatus.PASSED);
        assertThat(rule.evaluate(context(new BigDecimal("500"), VolatilityState.STABLE,
                null, null, null, LiquidityState.MODERATE)).status())
                .isEqualTo(EvaluationStatus.FAILED);
        assertThat(rule.evaluate(context(new BigDecimal("500"), VolatilityState.STABLE,
                null, null, null, LiquidityState.UNKNOWN)).status())
                .isEqualTo(EvaluationStatus.INSUFFICIENT_DATA);
    }

    @Test
    void optionRulesRejectInvalidThresholds() {
        assertThatThrownBy(() -> new OptionsRules.PcrAtLeast("pcr",
                BigDecimal.ZERO))
                .isInstanceOf(com.tradingassistant.strategy.exception
                        .StrategyValidationException.class)
                .hasMessageContaining("threshold");
        assertThatThrownBy(() -> new OptionsRules.MaxPainWithinPercent("maxpain",
                new BigDecimal("-1")))
                .isInstanceOf(com.tradingassistant.strategy.exception
                        .StrategyValidationException.class)
                .hasMessageContaining("tolerancePercent");
        assertThatThrownBy(() -> new OptionsRules.ExpectedMoveAtLeastPercent("move",
                new BigDecimal("-1")))
                .isInstanceOf(com.tradingassistant.strategy.exception
                        .StrategyValidationException.class)
                .hasMessageContaining("percentOfSpot");
    }

    @Test
    void rulesNeverReportAPassWithoutAPassedStatus() {
        var rule = new OptionsRules.PcrAtLeast("pcr.at-least", new BigDecimal("1.5"));

        RuleEvaluation evaluation = rule.evaluate(context(new BigDecimal("500"),
                VolatilityState.STABLE, new BigDecimal("2"), null, null,
                LiquidityState.LIQUID));

        assertThat(evaluation.passed()).isTrue();
        assertThat(evaluation.status()).isEqualTo(EvaluationStatus.PASSED);
        assertThat(evaluation.metadata()).containsEntry("threshold", "1.5");
    }

    @Test
    void optionRulesNeedAnOptionsAssessment() {
        StrategyEvaluationContext withoutOptions = StrategyEvaluationContext.of(
                StrategyTestFixtures.marketAssessment());

        assertThat(new OptionsRules.PcrAtLeast("pcr", new BigDecimal("1")).evaluate(
                withoutOptions).status()).isEqualTo(EvaluationStatus.INSUFFICIENT_DATA);
        assertThat(new OptionsRules.MaxPainWithinPercent("maxpain",
                new BigDecimal("1")).evaluate(withoutOptions).status())
                .isEqualTo(EvaluationStatus.INSUFFICIENT_DATA);
        assertThat(new OptionsRules.ExpectedMoveAtLeastPercent("move",
                new BigDecimal("1")).evaluate(withoutOptions).status())
                .isEqualTo(EvaluationStatus.INSUFFICIENT_DATA);
        assertThat(new OptionsRules.LiquidityAtLeast("liquidity").evaluate(withoutOptions)
                .status()).isEqualTo(EvaluationStatus.INSUFFICIENT_DATA);
        assertThat(withoutOptions.optionsAssessment()).isEqualTo(Optional.empty());
    }
}
