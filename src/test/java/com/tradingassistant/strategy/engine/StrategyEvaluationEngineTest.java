package com.tradingassistant.strategy.engine;

import com.tradingassistant.strategy.StrategyTestFixtures;
import com.tradingassistant.strategy.api.StrategyRule;
import com.tradingassistant.strategy.config.StrategyEngineConfig;
import com.tradingassistant.strategy.domain.EvaluationStatus;
import com.tradingassistant.strategy.domain.RuleEvaluation;
import com.tradingassistant.strategy.domain.StrategyCategory;
import com.tradingassistant.strategy.domain.StrategyDefinition;
import com.tradingassistant.strategy.domain.StrategyEvaluation;
import com.tradingassistant.strategy.domain.StrategyEvaluationContext;
import com.tradingassistant.strategy.domain.StrategyInput;
import com.tradingassistant.strategy.exception.StrategyEvaluationException;
import com.tradingassistant.strategy.exception.StrategyValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyEvaluationEngineTest {

    private final StrategyEvaluationContext context = StrategyTestFixtures.context(
            StrategyTestFixtures.ema("100"));

    private static StrategyRule rule(String id, EvaluationStatus status) {
        return new StrategyRule() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public RuleEvaluation evaluate(StrategyEvaluationContext context) {
                return new RuleEvaluation(id, status, "stub " + status, Map.of());
            }
        };
    }

    private static StrategyRule failingRule(String id) {
        return new StrategyRule() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public RuleEvaluation evaluate(StrategyEvaluationContext context) {
                throw new IllegalStateException("boom");
            }
        };
    }

    private static StrategyDefinition definition(String id, boolean enabled,
                                                 Set<StrategyInput> inputs,
                                                 StrategyRule... rules) {
        return new StrategyDefinition(id, "name " + id, "", enabled, StrategyCategory.CUSTOM,
                inputs, List.of(rules));
    }

    private StrategyEvaluationEngine engine() {
        return new StrategyEvaluationEngine(StrategyEngineConfig.DEFAULT, Runnable::run);
    }

    @Test
    void passesAStrategyWhenEveryRulePasses() {
        StrategyEvaluation evaluation = engine().evaluate(context,
                definition("A", true, Set.of(),
                        rule("one", EvaluationStatus.PASSED),
                        rule("two", EvaluationStatus.PASSED)));

        assertThat(evaluation.status()).isEqualTo(EvaluationStatus.PASSED);
        assertThat(evaluation.passed()).isTrue();
        assertThat(evaluation.ruleCount()).isEqualTo(2);
        assertThat(evaluation.instrument()).isEqualTo(StrategyTestFixtures.SPOT);
        assertThat(evaluation.summary()).isEqualTo("2 of 2 rules passed");
    }

    @Test
    void failsAStrategyWhenAnyRuleFails() {
        StrategyEvaluation evaluation = engine().evaluate(context,
                definition("A", true, Set.of(),
                        rule("one", EvaluationStatus.PASSED),
                        rule("two", EvaluationStatus.FAILED)));

        assertThat(evaluation.status()).isEqualTo(EvaluationStatus.FAILED);
        assertThat(evaluation.passed()).isFalse();
    }

    @Test
    void reportsPartialWhenSomeRulesPassAndOthersHaveNoData() {
        StrategyEvaluation evaluation = engine().evaluate(context,
                definition("A", true, Set.of(),
                        rule("one", EvaluationStatus.PASSED),
                        rule("two", EvaluationStatus.INSUFFICIENT_DATA)));

        assertThat(evaluation.status()).isEqualTo(EvaluationStatus.PARTIAL);
    }

    @Test
    void reportsInsufficientDataWhenNoRuleCouldBeDecided() {
        StrategyEvaluation evaluation = engine().evaluate(context,
                definition("A", true, Set.of(),
                        rule("one", EvaluationStatus.INSUFFICIENT_DATA),
                        rule("two", EvaluationStatus.INSUFFICIENT_DATA)));

        assertThat(evaluation.status()).isEqualTo(EvaluationStatus.INSUFFICIENT_DATA);
    }

    @Test
    void reportsUnknownWhenEveryRuleIsUnknown() {
        StrategyEvaluation evaluation = engine().evaluate(context,
                definition("A", true, Set.of(),
                        rule("one", EvaluationStatus.UNKNOWN),
                        rule("two", EvaluationStatus.UNKNOWN)));

        assertThat(evaluation.status()).isEqualTo(EvaluationStatus.UNKNOWN);
    }

    @Test
    void skipsDisabledStrategies() {
        List<StrategyEvaluation> evaluations = engine().evaluate(context, List.of(
                definition("A", false, Set.of(), rule("one", EvaluationStatus.PASSED)),
                definition("B", true, Set.of(), rule("two", EvaluationStatus.PASSED))));

        assertThat(evaluations).extracting(StrategyEvaluation::strategyId)
                .containsExactly("B");
    }

    @Test
    void strictValidationReportsMissingDeclaredInputsWithoutEvaluatingRules() {
        StrategyEvaluation evaluation = engine().evaluate(context,
                definition("A", true, Set.of(StrategyInput.OPTIONS_ASSESSMENT),
                        rule("one", EvaluationStatus.PASSED)));

        assertThat(evaluation.status()).isEqualTo(EvaluationStatus.INSUFFICIENT_DATA);
        assertThat(evaluation.ruleEvaluations()).singleElement()
                .satisfies(rule -> assertThat(rule.reason())
                        .contains("OPTIONS_ASSESSMENT"));
    }

    @Test
    void lenientEvaluationRunsRulesEvenWhenADeclaredInputIsMissing() {
        StrategyEvaluationEngine lenient = new StrategyEvaluationEngine(
                StrategyEngineConfig.builder().strictValidation(false).build(),
                Runnable::run);

        StrategyEvaluation evaluation = lenient.evaluate(context,
                definition("A", true, Set.of(StrategyInput.OPTIONS_ASSESSMENT),
                        rule("one", EvaluationStatus.PASSED)));

        assertThat(evaluation.status()).isEqualTo(EvaluationStatus.PASSED);
        assertThat(evaluation.ruleEvaluations()).singleElement()
                .satisfies(rule -> assertThat(rule.status())
                        .isEqualTo(EvaluationStatus.PASSED));
    }

    @Test
    void containsARuleFailureWhenFailFastIsDisabled() {
        StrategyEvaluation evaluation = engine().evaluate(context,
                definition("A", true, Set.of(), failingRule("broken")));

        assertThat(evaluation.status()).isEqualTo(EvaluationStatus.UNKNOWN);
        assertThat(evaluation.ruleEvaluations()).singleElement()
                .satisfies(rule -> assertThat(rule.reason()).contains("failed"));
    }

    @Test
    void propagatesARuleFailureWhenFailFastIsEnabled() {
        StrategyEvaluationEngine failFast = new StrategyEvaluationEngine(
                StrategyEngineConfig.builder().failFast(true).build(), Runnable::run);

        assertThatThrownBy(() -> failFast.evaluate(context,
                definition("A", true, Set.of(), failingRule("broken"))))
                .isInstanceOf(StrategyEvaluationException.class)
                .hasMessageContaining("rule broken failed");
    }

    @Test
    void evaluatesInParallelWhilePreservingInputOrder() {
        StrategyEngineConfig config = StrategyEngineConfig.builder()
                .parallelEvaluation(true)
                .build();
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            StrategyEvaluationEngine parallel = new StrategyEvaluationEngine(config,
                    executor);
            List<StrategyDefinition> definitions = List.of(
                    definition("C", true, Set.of(), rule("c", EvaluationStatus.PASSED)),
                    definition("A", true, Set.of(), rule("a", EvaluationStatus.FAILED)),
                    definition("B", true, Set.of(),
                            rule("b1", EvaluationStatus.PASSED),
                            rule("b2", EvaluationStatus.PASSED)));

            List<StrategyEvaluation> first = parallel.evaluate(context, definitions);
            List<StrategyEvaluation> second = parallel.evaluate(context, definitions);

            assertThat(first).extracting(StrategyEvaluation::strategyId)
                    .containsExactly("C", "A", "B");
            assertThat(first).isEqualTo(second);
            assertThat(first).extracting(StrategyEvaluation::status)
                    .containsExactly(EvaluationStatus.PASSED, EvaluationStatus.FAILED,
                            EvaluationStatus.PASSED);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsDuplicateStrategiesInOneBatch() {
        StrategyDefinition first = definition("A", true, Set.of(),
                rule("one", EvaluationStatus.PASSED));
        StrategyDefinition second = definition("A", true, Set.of(),
                rule("two", EvaluationStatus.PASSED));

        assertThatThrownBy(() -> engine().evaluate(context, List.of(first, second)))
                .isInstanceOf(StrategyValidationException.class)
                .hasMessageContaining("duplicate strategy id A");
    }

    @Test
    void rejectsMoreStrategiesThanConfigured() {
        StrategyEvaluationEngine limited = new StrategyEvaluationEngine(
                StrategyEngineConfig.builder().maxStrategies(1).build(), Runnable::run);

        assertThatThrownBy(() -> limited.evaluate(context, List.of(
                definition("A", true, Set.of(), rule("one", EvaluationStatus.PASSED)),
                definition("B", true, Set.of(), rule("two", EvaluationStatus.PASSED)))))
                .isInstanceOf(StrategyValidationException.class)
                .hasMessageContaining("maxStrategies");
    }

    @Test
    void rejectsANullContextOrNullStrategyList() {
        assertThatThrownBy(() -> engine().evaluate(null, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> engine().evaluate(context,
                (List<StrategyDefinition>) null))
                .isInstanceOf(StrategyValidationException.class)
                .hasMessageContaining("strategies");
    }

    @Test
    void returnsAnEmptyListWhenEverythingIsDisabled() {
        assertThat(engine().evaluate(context, List.of(
                definition("A", false, Set.of(), rule("one", EvaluationStatus.PASSED)))))
                .isEmpty();
    }

    @Test
    void isDeterministicForTheSameInputs() {
        StrategyDefinition definition = definition("A", true, Set.of(),
                rule("one", EvaluationStatus.PASSED),
                rule("two", EvaluationStatus.FAILED));

        assertThat(engine().evaluate(context, List.of(definition)))
                .isEqualTo(engine().evaluate(context, List.of(definition)));
    }

    @Test
    void returnsImmutableEvaluations() {
        StrategyEvaluation evaluation = engine().evaluate(context,
                definition("A", true, Set.of(), rule("one", EvaluationStatus.PASSED)));

        assertThatThrownBy(() -> evaluation.ruleEvaluations().add(
                rule("two", EvaluationStatus.PASSED)
                        .evaluate(context)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsAnExecutorThatIsNull() {
        assertThatThrownBy(() -> new StrategyEvaluationEngine(StrategyEngineConfig.DEFAULT,
                null))
                .isInstanceOf(NullPointerException.class);
    }
}
