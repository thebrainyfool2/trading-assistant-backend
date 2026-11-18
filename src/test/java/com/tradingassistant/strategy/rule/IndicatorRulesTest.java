package com.tradingassistant.strategy.rule;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.strategy.StrategyTestFixtures;
import com.tradingassistant.strategy.domain.EvaluationStatus;
import com.tradingassistant.strategy.domain.RuleEvaluation;
import com.tradingassistant.strategy.domain.StrategyEvaluationContext;
import com.tradingassistant.strategy.exception.StrategyValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IndicatorRulesTest {

    private final IndicatorRules.CloseAboveIndicator closeAbove =
            new IndicatorRules.CloseAboveIndicator("ema.close-above", "EMA");

    @Test
    void closeAbovePassesWhenTheCloseExceedsTheIndicator() {
        StrategyEvaluationContext context = StrategyTestFixtures.context(
                StrategyTestFixtures.ema("100"));

        RuleEvaluation evaluation = closeAbove.evaluate(context);

        assertThat(evaluation.status()).isEqualTo(EvaluationStatus.PASSED);
        assertThat(evaluation.passed()).isTrue();
        assertThat(evaluation.metadata()).containsEntry("value", "100");
    }

    @Test
    void closeAboveFailsWhenTheCloseIsBelowTheIndicator() {
        StrategyEvaluationContext context = StrategyTestFixtures.context("95", 1_000L,
                StrategyTestFixtures.ema("100"));

        RuleEvaluation evaluation = closeAbove.evaluate(context);

        assertThat(evaluation.status()).isEqualTo(EvaluationStatus.FAILED);
        assertThat(evaluation.passed()).isFalse();
    }

    @Test
    void closeAboveReportsInsufficientDataWithoutTheIndicatorOrCandle() {
        RuleEvaluation withoutIndicator = closeAbove.evaluate(
                StrategyTestFixtures.context("105", 1_000L));
        RuleEvaluation withoutCandle = closeAbove.evaluate(
                new StrategyEvaluationContext(StrategyTestFixtures.marketAssessment(),
                        java.util.Optional.empty(), java.util.Optional.empty(),
                        StrategyTestFixtures.indicators(StrategyTestFixtures.ema("100"))));

        assertThat(withoutIndicator.status())
                .isEqualTo(EvaluationStatus.INSUFFICIENT_DATA);
        assertThat(withoutIndicator.reason()).contains("EMA");
        assertThat(withoutCandle.status())
                .isEqualTo(EvaluationStatus.INSUFFICIENT_DATA);
        assertThat(withoutCandle.reason()).contains("close");
    }

    @Test
    void indicatorAtLeastComparesAgainstTheThreshold() {
        var rule = new IndicatorRules.IndicatorAtLeast("adx.at-least", "ADX", "adx",
                new BigDecimal("20"));
        IndicatorResult strong = StrategyTestFixtures.indicator("ADX", "adx", "25");
        IndicatorResult weak = StrategyTestFixtures.indicator("ADX", "adx", "12");

        assertThat(rule.evaluate(StrategyTestFixtures.context(strong)).status())
                .isEqualTo(EvaluationStatus.PASSED);
        assertThat(rule.evaluate(StrategyTestFixtures.context(weak)).status())
                .isEqualTo(EvaluationStatus.FAILED);
        assertThat(rule.evaluate(StrategyTestFixtures.context("105", 1_000L)).status())
                .isEqualTo(EvaluationStatus.INSUFFICIENT_DATA);
    }

    @Test
    void indicatorAtMostComparesAgainstTheThreshold() {
        var rule = new IndicatorRules.IndicatorAtMost("rsi.at-most", "RSI", "value",
                new BigDecimal("45"));
        IndicatorResult low = StrategyTestFixtures.indicator("RSI", "value", "38");
        IndicatorResult high = StrategyTestFixtures.indicator("RSI", "value", "62");

        assertThat(rule.evaluate(StrategyTestFixtures.context(low)).status())
                .isEqualTo(EvaluationStatus.PASSED);
        assertThat(rule.evaluate(StrategyTestFixtures.context(high)).status())
                .isEqualTo(EvaluationStatus.FAILED);
        assertThat(rule.evaluate(StrategyTestFixtures.context("105", 1_000L)).status())
                .isEqualTo(EvaluationStatus.INSUFFICIENT_DATA);
    }

    @Test
    void supertrendPassesOnlyForAPositiveDirection() {
        var rule = new IndicatorRules.SupertrendBullish("supertrend.positive");
        IndicatorResult up = StrategyTestFixtures.indicator("SUPERTREND", "direction", "1");
        IndicatorResult down = StrategyTestFixtures.indicator("SUPERTREND", "direction", "-1");

        assertThat(rule.evaluate(StrategyTestFixtures.context(up)).status())
                .isEqualTo(EvaluationStatus.PASSED);
        assertThat(rule.evaluate(StrategyTestFixtures.context(down)).status())
                .isEqualTo(EvaluationStatus.FAILED);
        assertThat(rule.evaluate(StrategyTestFixtures.context("105", 1_000L)).status())
                .isEqualTo(EvaluationStatus.INSUFFICIENT_DATA);
    }

    @Test
    void macdHistogramRuleRequiresAPositiveValue() {
        var rule = new IndicatorRules.MacdHistogramPositive("macd.histogram-positive");
        IndicatorResult positive = StrategyTestFixtures.indicator("MACD", "histogram", "1.5");
        IndicatorResult negative = StrategyTestFixtures.indicator("MACD", "histogram", "-0.5");

        assertThat(rule.evaluate(StrategyTestFixtures.context(positive)).status())
                .isEqualTo(EvaluationStatus.PASSED);
        assertThat(rule.evaluate(StrategyTestFixtures.context(negative)).status())
                .isEqualTo(EvaluationStatus.FAILED);
        assertThat(rule.evaluate(StrategyTestFixtures.context("105", 1_000L)).status())
                .isEqualTo(EvaluationStatus.INSUFFICIENT_DATA);
    }

    @Test
    void rulesRejectBlankIdentifiers() {
        assertThatThrownBy(() -> new IndicatorRules.CloseAboveIndicator(" ", "EMA"))
                .isInstanceOf(StrategyValidationException.class)
                .hasMessageContaining("rule id");
        assertThatThrownBy(() -> new IndicatorRules.CloseAboveIndicator("ema", " "))
                .isInstanceOf(StrategyValidationException.class)
                .hasMessageContaining("indicator");
        assertThatThrownBy(() -> new IndicatorRules.IndicatorAtLeast("adx", "ADX", "adx",
                null))
                .isInstanceOf(StrategyValidationException.class)
                .hasMessageContaining("threshold");
    }

    @Test
    void ruleIdentifiersAreExposed() {
        assertThat(closeAbove.id()).isEqualTo("ema.close-above");
        assertThat(closeAbove.evaluate(StrategyTestFixtures.context(
                StrategyTestFixtures.ema("100"))).ruleId()).isEqualTo("ema.close-above");
    }
}
