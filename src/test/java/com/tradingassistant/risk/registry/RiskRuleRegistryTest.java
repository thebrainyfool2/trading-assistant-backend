package com.tradingassistant.risk.registry;

import com.tradingassistant.risk.api.RiskRule;
import com.tradingassistant.risk.domain.RiskRuleContext;
import com.tradingassistant.risk.domain.RiskRuleEvaluation;
import com.tradingassistant.risk.domain.RiskRuleStatus;
import com.tradingassistant.risk.exception.RiskValidationException;
import com.tradingassistant.risk.rule.RiskRules;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiskRuleRegistryTest {

    @Test
    void registersAndLooksUpARule() {
        RiskRuleRegistry registry = new RiskRuleRegistry();

        registry.register(new RiskRules.MaximumLeverage("A"));

        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.contains("A")).isTrue();
        assertThat(registry.lookup("A")).isPresent();
        assertThat(registry.lookup("missing")).isEmpty();
        assertThat(registry.lookup(null)).isEmpty();
    }

    @Test
    void rejectsDuplicateIdentifiers() {
        RiskRuleRegistry registry = new RiskRuleRegistry(
                List.of(new RiskRules.MaximumLeverage("A")));

        assertThatThrownBy(() -> registry.register(new RiskRules.MaximumQuantity("A")))
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("duplicate rule id A");
    }

    @Test
    void replacesARuleOfTheSameIdentifier() {
        RiskRuleRegistry registry = new RiskRuleRegistry(
                List.of(new RiskRules.MaximumLeverage("A")));

        registry.replace(new RiskRules.MaximumQuantity("A"));

        assertThat(registry.lookup("A")).get()
                .isInstanceOf(RiskRules.MaximumQuantity.class);
    }

    @Test
    void rejectsReplacingAnUnknownIdentifier() {
        RiskRuleRegistry registry = new RiskRuleRegistry();

        assertThatThrownBy(() -> registry.replace(new RiskRules.MaximumLeverage("A")))
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("unknown rule id A");
    }

    @Test
    void unregistersARule() {
        RiskRuleRegistry registry = new RiskRuleRegistry(
                List.of(new RiskRules.MaximumLeverage("A")));

        assertThat(registry.unregister("A")).isTrue();
        assertThat(registry.unregister("A")).isFalse();
        assertThat(registry.unregister(null)).isFalse();
        assertThat(registry.size()).isZero();
    }

    @Test
    void snapshotsAreSortedByIdentifier() {
        RiskRuleRegistry registry = new RiskRuleRegistry(
                List.of(new RiskRules.MaximumLeverage("B"),
                        new RiskRules.MaximumQuantity("A")));

        assertThat(registry.snapshot()).extracting(RiskRule::id).containsExactly("A", "B");
    }

    @Test
    void snapshotsAreImmutable() {
        RiskRuleRegistry registry = new RiskRuleRegistry(
                List.of(new RiskRules.MaximumLeverage("A")));
        List<RiskRule> snapshot = registry.snapshot();

        assertThatThrownBy(() -> snapshot.add(new RiskRules.MaximumQuantity("B")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullAndBlankRules() {
        RiskRuleRegistry registry = new RiskRuleRegistry();

        assertThatThrownBy(() -> registry.register(null))
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("rule must not be null");
        assertThatThrownBy(() -> registry.register(new RiskRules.MaximumLeverage(" ")))
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("rule id");
    }

    @Test
    void theBuiltInCatalogContainsEveryRuleInOrder() {
        List<RiskRule> rules = BuiltInRiskRules.all();

        assertThat(rules).hasSize(15);
        assertThat(rules).extracting(RiskRule::id).containsExactly(
                BuiltInRiskRules.QUALIFICATION_ELIGIBLE,
                BuiltInRiskRules.ATR_STOP_VALIDATION,
                BuiltInRiskRules.MINIMUM_STOP_DISTANCE,
                BuiltInRiskRules.MAXIMUM_STOP_DISTANCE,
                BuiltInRiskRules.VOLATILITY_MULTIPLIER,
                BuiltInRiskRules.MAXIMUM_RISK_PERCENT,
                BuiltInRiskRules.DAILY_RISK_LIMIT,
                BuiltInRiskRules.SESSION_LOSS_CAP,
                BuiltInRiskRules.MAXIMUM_OPEN_EXPOSURE,
                BuiltInRiskRules.MAXIMUM_LEVERAGE,
                BuiltInRiskRules.MAXIMUM_QUANTITY,
                BuiltInRiskRules.MINIMUM_REWARD_RISK,
                BuiltInRiskRules.GAP_PROTECTION,
                BuiltInRiskRules.OPTION_PREMIUM_RISK,
                BuiltInRiskRules.INSTRUMENT_OVERRIDE);
    }

    @Test
    void everyCatalogRuleIsIndependentlyEvaluable() {
        var context = com.tradingassistant.risk.RiskTestFixtures.ruleContext();

        assertThat(BuiltInRiskRules.all()).allSatisfy(rule ->
                assertThat(rule.evaluate(context).status()).isNotEqualTo(RiskRuleStatus.UNKNOWN));
    }

    @Test
    void aRegisteredRuleCanBeReusedWithoutSharingState() {
        RiskRuleRegistry registry = new RiskRuleRegistry(BuiltInRiskRules.all());
        RiskRuleEvaluation first = registry.lookup(BuiltInRiskRules.MAXIMUM_LEVERAGE)
                .orElseThrow()
                .evaluate(com.tradingassistant.risk.RiskTestFixtures.ruleContext());

        assertThat(first.passed()).isTrue();
        assertThat(first.metadata()).isEqualTo(Map.of("requiredLeverage", "0.5",
                "maximumLeverage", "5"));
        assertThat(registry.size()).isEqualTo(15);
    }

    @Test
    void replacementDoesNotAffectASnapshotAlreadyTaken() {
        RiskRuleRegistry registry = new RiskRuleRegistry(
                List.of(new RiskRules.MaximumLeverage("A")));
        List<RiskRule> before = registry.snapshot();

        registry.replace(new RiskRules.MaximumQuantity("A"));

        assertThat(before.getFirst()).isInstanceOf(RiskRules.MaximumLeverage.class);
        assertThat(registry.snapshot().getFirst())
                .isInstanceOf(RiskRules.MaximumQuantity.class);
    }

    private record Stub(String id) implements RiskRule {
        @Override
        public RiskRuleEvaluation evaluate(RiskRuleContext context) {
            return RiskRuleEvaluation.passed(id, "stub", Map.of());
        }
    }
}
