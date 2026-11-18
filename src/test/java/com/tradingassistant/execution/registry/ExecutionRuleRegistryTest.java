package com.tradingassistant.execution.registry;

import com.tradingassistant.execution.ExecutionTestFixtures;
import com.tradingassistant.execution.api.ExecutionRule;
import com.tradingassistant.execution.domain.ExecutionRuleContext;
import com.tradingassistant.execution.domain.ExecutionRuleEvaluation;
import com.tradingassistant.execution.exception.ExecutionValidationException;
import com.tradingassistant.execution.rule.ExecutionRules;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionRuleRegistryTest {

    private static ExecutionRule rule(String id) {
        return new ExecutionRule() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public ExecutionRuleEvaluation evaluate(ExecutionRuleContext context) {
                return ExecutionRuleEvaluation.passed(id, "ok", Map.of());
            }
        };
    }

    @Test
    void registersAndLooksUpRules() {
        ExecutionRuleRegistry registry = new ExecutionRuleRegistry();
        registry.register(rule("A"));

        assertThat(registry.lookup("A")).isPresent();
        assertThat(registry.contains("A")).isTrue();
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateIds() {
        ExecutionRuleRegistry registry = new ExecutionRuleRegistry();
        registry.register(rule("A"));

        assertThatThrownBy(() -> registry.register(rule("A")))
                .isInstanceOf(ExecutionValidationException.class)
                .hasMessageContaining("duplicate rule");
    }

    @Test
    void replacesAKnownRuleOnly() {
        ExecutionRuleRegistry registry = new ExecutionRuleRegistry();
        registry.register(rule("A"));
        registry.replace(rule("A"));

        assertThat(registry.size()).isEqualTo(1);
        assertThatThrownBy(() -> registry.replace(rule("B")))
                .isInstanceOf(ExecutionValidationException.class)
                .hasMessageContaining("unknown rule");
    }

    @Test
    void unregistersRules() {
        ExecutionRuleRegistry registry = new ExecutionRuleRegistry();
        registry.register(rule("A"));

        assertThat(registry.unregister("A")).isTrue();
        assertThat(registry.unregister("A")).isFalse();
        assertThat(registry.unregister(null)).isFalse();
    }

    @Test
    void snapshotIsSortedByRuleId() {
        ExecutionRuleRegistry registry = new ExecutionRuleRegistry(List.of(rule("B"),
                rule("A")));

        assertThat(registry.snapshot()).extracting(ExecutionRule::id).containsExactly("A",
                "B");
    }

    @Test
    void blankLookupsYieldEmpty() {
        ExecutionRuleRegistry registry = new ExecutionRuleRegistry();

        assertThat(registry.lookup(" ")).isEmpty();
        assertThat(registry.lookup(null)).isEmpty();
        assertThat(registry.contains(null)).isFalse();
    }

    @Test
    void rejectsNullAndBlankRules() {
        ExecutionRuleRegistry registry = new ExecutionRuleRegistry();

        assertThatThrownBy(() -> registry.register(null))
                .isInstanceOf(ExecutionValidationException.class);
        assertThatThrownBy(() -> registry.register(rule(" ")))
                .isInstanceOf(ExecutionValidationException.class);
    }

    @Test
    void builtInCatalogRegistersCompletely() {
        ExecutionRuleRegistry registry = new ExecutionRuleRegistry(
                BuiltInExecutionRules.all());

        assertThat(registry.size()).isEqualTo(13);
        assertThat(registry.snapshot()).allSatisfy(rule ->
                assertThat(rule.evaluate(ExecutionTestFixtures.ruleContext()).passed())
                        .isTrue());
    }

    @Test
    void exposesTheRuleContract() {
        ExecutionRules.ValidQuantity rule = new ExecutionRules.ValidQuantity("VALID_QUANTITY");

        assertThat(rule.id()).isEqualTo("VALID_QUANTITY");
        assertThat(rule.evaluate(ExecutionTestFixtures.ruleContext()).passed()).isTrue();
    }
}
