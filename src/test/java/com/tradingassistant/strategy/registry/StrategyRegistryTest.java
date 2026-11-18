package com.tradingassistant.strategy.registry;

import com.tradingassistant.strategy.api.StrategyRule;
import com.tradingassistant.strategy.config.StrategyEngineConfig;
import com.tradingassistant.strategy.domain.EvaluationStatus;
import com.tradingassistant.strategy.domain.RuleEvaluation;
import com.tradingassistant.strategy.domain.StrategyCategory;
import com.tradingassistant.strategy.domain.StrategyDefinition;
import com.tradingassistant.strategy.domain.StrategyEvaluationContext;
import com.tradingassistant.strategy.domain.StrategyInput;
import com.tradingassistant.strategy.exception.StrategyValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyRegistryTest {

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

    private static StrategyDefinition definition(String id, boolean enabled,
                                                 StrategyRule... rules) {
        return new StrategyDefinition(id, "name " + id, "", enabled, StrategyCategory.CUSTOM,
                Set.of(StrategyInput.MARKET_ASSESSMENT),
                List.of(rules.length == 0 ? new StrategyRule[]{rule(id + ".rule")} : rules));
    }

    private final StrategyEngineConfig config = StrategyEngineConfig.DEFAULT;

    @Test
    void registersAndLooksUpDefinitions() {
        StrategyRegistry registry = new StrategyRegistry(config);

        registry.register(definition("B", true));
        registry.register(definition("A", false));

        assertThat(registry.size()).isEqualTo(2);
        assertThat(registry.contains("A")).isTrue();
        assertThat(registry.lookup("A")).isPresent();
        assertThat(registry.lookup("missing")).isEmpty();
        assertThat(registry.lookup(" ")).isEmpty();
    }

    @Test
    void rejectsDuplicateIdentifiers() {
        StrategyRegistry registry = new StrategyRegistry(config);
        registry.register(definition("A", true));

        assertThatThrownBy(() -> registry.register(definition("A", true)))
                .isInstanceOf(StrategyValidationException.class)
                .hasMessageContaining("duplicate strategy id A");
    }

    @Test
    void replacesAnExistingDefinition() {
        StrategyRegistry registry = new StrategyRegistry(config);
        registry.register(definition("A", true));

        registry.replace(definition("A", false));

        assertThat(registry.lookup("A").orElseThrow().enabled()).isFalse();
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void rejectsReplacingAnUnknownDefinition() {
        StrategyRegistry registry = new StrategyRegistry(config);

        assertThatThrownBy(() -> registry.replace(definition("A", true)))
                .isInstanceOf(StrategyValidationException.class)
                .hasMessageContaining("unknown strategy id A");
    }

    @Test
    void unregistersDefinitions() {
        StrategyRegistry registry = new StrategyRegistry(config);
        registry.register(definition("A", true));

        assertThat(registry.unregister("A")).isTrue();
        assertThat(registry.unregister("A")).isFalse();
        assertThat(registry.unregister(null)).isFalse();
        assertThat(registry.size()).isZero();
    }

    @Test
    void listsEnabledAndDisabledDefinitionsInStableOrder() {
        StrategyRegistry registry = new StrategyRegistry(config);
        registry.register(definition("C", true));
        registry.register(definition("A", false));
        registry.register(definition("B", true));

        assertThat(registry.enabled()).extracting(StrategyDefinition::id)
                .containsExactly("B", "C");
        assertThat(registry.disabled()).extracting(StrategyDefinition::id)
                .containsExactly("A");
        assertThat(registry.snapshot()).extracting(StrategyDefinition::id)
                .containsExactly("A", "B", "C");
    }

    @Test
    void snapshotsAreImmutable() {
        StrategyRegistry registry = new StrategyRegistry(config);
        registry.register(definition("A", true));

        List<StrategyDefinition> snapshot = registry.snapshot();

        assertThatThrownBy(() -> snapshot.add(definition("B", true)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> registry.enabled().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void enforcesTheConfiguredStrategyLimit() {
        StrategyRegistry registry = new StrategyRegistry(
                StrategyEngineConfig.builder().maxStrategies(1).build());
        registry.register(definition("A", true));

        assertThatThrownBy(() -> registry.register(definition("B", true)))
                .isInstanceOf(StrategyValidationException.class)
                .hasMessageContaining("maxStrategies");
    }

    @Test
    void enforcesTheConfiguredRuleLimit() {
        StrategyRegistry registry = new StrategyRegistry(
                StrategyEngineConfig.builder().maxRules(1).build());
        StrategyDefinition tooLarge = definition("A", true, rule("one"), rule("two"));

        assertThatThrownBy(() -> registry.register(tooLarge))
                .isInstanceOf(StrategyValidationException.class)
                .hasMessageContaining("maxRules");
    }

    @Test
    void seedsItselfFromAnInitialCollection() {
        StrategyRegistry registry = new StrategyRegistry(config,
                List.of(definition("B", true), definition("A", true)));

        assertThat(registry.size()).isEqualTo(2);
        assertThat(registry.snapshot()).extracting(StrategyDefinition::id)
                .containsExactly("A", "B");
    }

    @Test
    void rejectsNullSearchesAndDefinitions() {
        StrategyRegistry registry = new StrategyRegistry(config);

        assertThatThrownBy(() -> registry.register(null))
                .isInstanceOf(NullPointerException.class);
        assertThat(registry.lookup(null)).isEmpty();
        assertThat(registry.contains(null)).isFalse();
    }

    @Test
    void aRegisteredDefinitionCannotBeMutatedThroughTheRegistry() {
        StrategyRegistry registry = new StrategyRegistry(config);
        StrategyDefinition definition = definition("A", true);
        registry.register(definition);

        StrategyDefinition stored = registry.lookup("A").orElseThrow();

        assertThat(stored.enabled()).isTrue();
        assertThat(stored.withEnabled(false).enabled()).isFalse();
        assertThat(stored.enabled()).isTrue();
        assertThat(stored.rules()).extracting(StrategyRule::id)
                .containsExactly("A.rule");
        assertThat(stored.withEnabled(false).enabled()).isFalse();
    }
}
