package com.tradingassistant.qualification.registry;

import com.tradingassistant.qualification.api.QualificationRule;
import com.tradingassistant.qualification.config.TradeQualificationConfig;
import com.tradingassistant.qualification.exception.QualificationValidationException;
import com.tradingassistant.qualification.rule.QualificationRules;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QualificationRuleRegistryTest {

    private final TradeQualificationConfig config = TradeQualificationConfig.DEFAULT;

    @Test
    void registersAndLooksUpARule() {
        QualificationRuleRegistry registry = new QualificationRuleRegistry(config);

        registry.register(new QualificationRules.SessionOpen("A"));

        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.contains("A")).isTrue();
        assertThat(registry.lookup("A")).isPresent();
        assertThat(registry.lookup("missing")).isEmpty();
        assertThat(registry.lookup(null)).isEmpty();
    }

    @Test
    void rejectsDuplicateIdentifiers() {
        QualificationRuleRegistry registry = new QualificationRuleRegistry(config,
                List.of(new QualificationRules.SessionOpen("A")));

        assertThatThrownBy(() -> registry.register(new QualificationRules.MarketAssessmentReady("A")))
                .isInstanceOf(QualificationValidationException.class)
                .hasMessageContaining("duplicate rule id A");
    }

    @Test
    void replacesARuleOfTheSameIdentifier() {
        QualificationRuleRegistry registry = new QualificationRuleRegistry(config,
                List.of(new QualificationRules.SessionOpen("A")));

        registry.replace(new QualificationRules.MarketAssessmentReady("A"));

        assertThat(registry.lookup("A")).get()
                .isInstanceOf(QualificationRules.MarketAssessmentReady.class);
    }

    @Test
    void rejectsReplacingAnUnknownIdentifier() {
        QualificationRuleRegistry registry = new QualificationRuleRegistry(config);

        assertThatThrownBy(() -> registry.replace(new QualificationRules.SessionOpen("A")))
                .isInstanceOf(QualificationValidationException.class)
                .hasMessageContaining("unknown rule id A");
    }

    @Test
    void unregistersARule() {
        QualificationRuleRegistry registry = new QualificationRuleRegistry(config,
                List.of(new QualificationRules.SessionOpen("A")));

        assertThat(registry.unregister("A")).isTrue();
        assertThat(registry.unregister("A")).isFalse();
        assertThat(registry.unregister(null)).isFalse();
        assertThat(registry.size()).isZero();
    }

    @Test
    void snapshotsAreSortedByIdentifier() {
        QualificationRuleRegistry registry = new QualificationRuleRegistry(config,
                List.of(new QualificationRules.SessionOpen("B"),
                        new QualificationRules.MarketAssessmentReady("A")));

        assertThat(registry.snapshot()).extracting(QualificationRule::id)
                .containsExactly("A", "B");
    }

    @Test
    void snapshotsAreImmutable() {
        QualificationRuleRegistry registry = new QualificationRuleRegistry(config,
                List.of(new QualificationRules.SessionOpen("A")));
        List<QualificationRule> snapshot = registry.snapshot();

        assertThatThrownBy(() -> snapshot.add(new QualificationRules.SessionOpen("B")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void enforcesTheConfiguredRuleLimit() {
        TradeQualificationConfig limited = TradeQualificationConfig.builder().maxRules(1).build();
        QualificationRuleRegistry registry = new QualificationRuleRegistry(limited,
                List.of(new QualificationRules.SessionOpen("A")));

        assertThatThrownBy(() -> registry.register(new QualificationRules.SessionOpen("B")))
                .isInstanceOf(QualificationValidationException.class)
                .hasMessageContaining("maxRules 1");
    }

    @Test
    void rejectsNullAndBlankRules() {
        QualificationRuleRegistry registry = new QualificationRuleRegistry(config);

        assertThatThrownBy(() -> registry.register(null))
                .isInstanceOf(QualificationValidationException.class)
                .hasMessageContaining("rule must not be null");
        assertThatThrownBy(() -> registry.register(new QualificationRules.SessionOpen(" ")))
                .isInstanceOf(QualificationValidationException.class)
                .hasMessageContaining("rule id");
    }
}
