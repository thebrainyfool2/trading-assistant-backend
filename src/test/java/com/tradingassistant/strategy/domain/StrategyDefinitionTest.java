package com.tradingassistant.strategy.domain;

import com.tradingassistant.strategy.api.StrategyRule;
import com.tradingassistant.strategy.exception.StrategyValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyDefinitionTest {

    private static StrategyRule rule(String id) {
        return new StrategyRule() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public RuleEvaluation evaluate(StrategyEvaluationContext context) {
                return RuleEvaluation.passed(id, "stub", Map.of());
            }
        };
    }

    private static StrategyDefinition definition(String id, String name,
                                                 StrategyRule... rules) {
        return new StrategyDefinition(id, name, "description", true, StrategyCategory.TREND,
                Set.of(StrategyInput.MARKET_ASSESSMENT), List.of(rules));
    }

    @Test
    void keepsIdentityAndRulesInOrder() {
        StrategyDefinition definition = definition("EMA_TREND", "EMA Trend",
                rule("one"), rule("two"));

        assertThat(definition.id()).isEqualTo("EMA_TREND");
        assertThat(definition.name()).isEqualTo("EMA Trend");
        assertThat(definition.ruleCount()).isEqualTo(2);
        assertThat(definition.rules()).extracting(StrategyRule::id)
                .containsExactly("one", "two");
        assertThat(definition.enabled()).isTrue();
    }

    @Test
    void rejectsBlankIdentity() {
        assertThatThrownBy(() -> definition(" ", "name", rule("one")))
                .isInstanceOf(StrategyValidationException.class)
                .hasMessageContaining("id");
        assertThatThrownBy(() -> definition("id", " ", rule("one")))
                .isInstanceOf(StrategyValidationException.class)
                .hasMessageContaining("name");
    }

    @Test
    void rejectsAnEmptyRuleList() {
        assertThatThrownBy(() -> definition("id", "name"))
                .isInstanceOf(StrategyValidationException.class)
                .hasMessageContaining("rules");
    }

    @Test
    void rejectsDuplicateRuleIdentifiers() {
        assertThatThrownBy(() -> definition("id", "name", rule("same"), rule("same")))
                .isInstanceOf(StrategyValidationException.class)
                .hasMessageContaining("duplicate rule id");
    }

    @Test
    void rejectsBlankRuleIdentifiers() {
        assertThatThrownBy(() -> definition("id", "name", rule(" ")))
                .isInstanceOf(StrategyValidationException.class)
                .hasMessageContaining("rule id");
    }

    @Test
    void exposesImmutableCollections() {
        StrategyDefinition definition = definition("id", "name", rule("one"));

        assertThatThrownBy(() -> definition.rules().add(rule("two")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> definition.requiredInputs().add(StrategyInput.CANDLE))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void createsAnEnabledCopyWithoutMutatingTheOriginal() {
        StrategyDefinition definition = definition("id", "name", rule("one"));

        StrategyDefinition disabled = definition.withEnabled(false);

        assertThat(disabled.enabled()).isFalse();
        assertThat(definition.enabled()).isTrue();
        assertThat(disabled.rules()).isEqualTo(definition.rules());
    }

    @Test
    void rejectsMissingCategory() {
        assertThatThrownBy(() -> new StrategyDefinition("id", "name", "", true, null,
                Set.of(), List.of(rule("one"))))
                .isInstanceOf(NullPointerException.class);
    }
}
