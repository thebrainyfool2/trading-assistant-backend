package com.tradingassistant.qualification.engine;

import com.tradingassistant.qualification.QualificationTestFixtures;
import com.tradingassistant.qualification.api.QualificationRule;
import com.tradingassistant.qualification.config.TradeQualificationConfig;
import com.tradingassistant.qualification.domain.QualificationRuleEvaluation;
import com.tradingassistant.qualification.domain.QualificationStatus;
import com.tradingassistant.qualification.domain.RuleStatus;
import com.tradingassistant.qualification.domain.TradeQualification;
import com.tradingassistant.qualification.domain.TradeQualificationContext;
import com.tradingassistant.qualification.exception.QualificationEvaluationException;
import com.tradingassistant.qualification.exception.QualificationValidationException;
import com.tradingassistant.qualification.registry.BuiltInQualificationRules;
import com.tradingassistant.qualification.registry.QualificationRuleRegistry;
import com.tradingassistant.qualification.rule.QualificationRules;
import com.tradingassistant.strategy.domain.EvaluationStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradeQualificationEngineTest {

    @Test
    void qualifiesWhenEveryRegisteredRulePasses() {
        TradeQualification qualification = engine().evaluate(QualificationTestFixtures.context());

        assertThat(qualification.status()).isEqualTo(QualificationStatus.QUALIFIED);
        assertThat(qualification.qualified()).isTrue();
        assertThat(qualification.ruleCount()).isEqualTo(11);
        assertThat(qualification.summary()).contains("11 of 11 rules passed");
    }

    @Test
    void disqualifiesWhenAnyRuleFails() {
        TradeQualification qualification = engine().evaluate(QualificationTestFixtures.context(
                QualificationTestFixtures.marketAssessment(
                        com.tradingassistant.intelligence.domain.ConfidenceLevel.MEDIUM)));

        assertThat(qualification.status()).isEqualTo(QualificationStatus.DISQUALIFIED);
        assertThat(qualification.qualified()).isFalse();
        assertThat(qualification.rule(BuiltInQualificationRules.MARKET_CONFIDENCE))
                .get()
                .extracting(QualificationRuleEvaluation::status)
                .isEqualTo(RuleStatus.FAILED);
    }

    @Test
    void reportsPartialWhenSomeRulesPassAndOthersCannotBeDecided() {
        TradeQualification qualification = engine().evaluate(
                QualificationTestFixtures.context(EvaluationStatus.INSUFFICIENT_DATA));

        assertThat(qualification.status()).isEqualTo(QualificationStatus.PARTIAL);
        assertThat(qualification.rule(BuiltInQualificationRules.STRATEGY_STATUS_PASSED))
                .get()
                .extracting(QualificationRuleEvaluation::status)
                .isEqualTo(RuleStatus.INSUFFICIENT_DATA);
    }

    @Test
    void reportsInsufficientDataWhenNoRuleCanBeDecided() {
        TradeQualification qualification = engine(TradeQualificationConfig.DEFAULT,
                new FixedRule("A", RuleStatus.INSUFFICIENT_DATA),
                new FixedRule("B", RuleStatus.INSUFFICIENT_DATA))
                .evaluate(QualificationTestFixtures.context());

        assertThat(qualification.status()).isEqualTo(QualificationStatus.INSUFFICIENT_DATA);
        assertThat(qualification.summary()).contains("0 of 2 rules passed");
    }

    @Test
    void reportsUnknownWhenAContainedFailureLeavesNoDecision() {
        TradeQualification qualification = engine(TradeQualificationConfig.DEFAULT,
                new ThrowingRule("BROKEN"))
                .evaluate(QualificationTestFixtures.context());

        assertThat(qualification.status()).isEqualTo(QualificationStatus.UNKNOWN);
        assertThat(qualification.ruleEvaluations().getFirst().status())
                .isEqualTo(RuleStatus.UNKNOWN);
    }

    @Test
    void propagatesARuleFailureWhenFailFastIsEnabled() {
        TradeQualificationConfig config = TradeQualificationConfig.builder()
                .failFast(true)
                .build();

        assertThatThrownBy(() -> engine(config, new ThrowingRule("BROKEN"))
                .evaluate(QualificationTestFixtures.context()))
                .isInstanceOf(QualificationEvaluationException.class)
                .hasMessageContaining("BROKEN");
    }

    @Test
    void treatsPartialResultsAsQualifiedWhenConfigured() {
        TradeQualificationConfig config = TradeQualificationConfig.builder()
                .allowPartialQualification(true)
                .build();

        TradeQualification qualification = engine(config).evaluate(
                QualificationTestFixtures.context(EvaluationStatus.INSUFFICIENT_DATA));

        assertThat(qualification.status()).isEqualTo(QualificationStatus.QUALIFIED);
        assertThat(qualification.summary()).contains("partial qualification accepted");
        assertThat(qualification.rule(BuiltInQualificationRules.STRATEGY_STATUS_PASSED))
                .get()
                .extracting(QualificationRuleEvaluation::status)
                .isEqualTo(RuleStatus.INSUFFICIENT_DATA);
    }

    @Test
    void strictValidationReportsAMissingRequiredOptionAssessmentWithoutEvaluatingRules() {
        TradeQualificationConfig config = TradeQualificationConfig.builder()
                .requireOptions(true)
                .build();

        TradeQualification qualification = engine(config)
                .evaluate(QualificationTestFixtures.context());

        assertThat(qualification.status()).isEqualTo(QualificationStatus.INSUFFICIENT_DATA);
        assertThat(qualification.summary()).contains("required option assessment");
        assertThat(qualification.ruleEvaluations())
                .extracting(QualificationRuleEvaluation::status)
                .containsOnly(RuleStatus.INSUFFICIENT_DATA);
    }

    @Test
    void lenientValidationLetsRulesReportTheMissingOptionAssessmentThemselves() {
        TradeQualificationConfig config = TradeQualificationConfig.builder()
                .requireOptions(true)
                .strictValidation(false)
                .build();

        TradeQualification qualification = engine(config)
                .evaluate(QualificationTestFixtures.context());

        assertThat(qualification.status()).isEqualTo(QualificationStatus.PARTIAL);
        assertThat(qualification.rule(BuiltInQualificationRules.OPTIONS_REQUIRED))
                .get()
                .extracting(QualificationRuleEvaluation::status)
                .isEqualTo(RuleStatus.INSUFFICIENT_DATA);
    }

    @Test
    void parallelEvaluationPreservesRuleOrderAndOutcomes() {
        TradeQualificationConfig config = TradeQualificationConfig.builder()
                .parallelEvaluation(true)
                .build();
        QualificationRuleRegistry parallelRegistry = new QualificationRuleRegistry(config,
                BuiltInQualificationRules.all(config, QualificationTestFixtures.ledger()));
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            TradeQualification parallel = new TradeQualificationEngine(config, parallelRegistry,
                    executor).evaluate(QualificationTestFixtures.context());
            TradeQualification sequential = engine().evaluate(QualificationTestFixtures.context());

            assertThat(parallel.ruleEvaluations())
                    .extracting(QualificationRuleEvaluation::ruleId)
                    .containsExactlyElementsOf(sequential.ruleEvaluations().stream()
                            .map(QualificationRuleEvaluation::ruleId).toList());
            assertThat(parallel.status()).isEqualTo(sequential.status());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void usesTheRegistrySnapshotWhenNoExplicitRuleSetIsGiven() {
        TradeQualificationConfig config = TradeQualificationConfig.DEFAULT;
        QualificationRuleRegistry registry = new QualificationRuleRegistry(config,
                List.of(new FixedRule("ONLY", RuleStatus.PASSED)));

        TradeQualification qualification = new TradeQualificationEngine(config, registry,
                Runnable::run).evaluate(QualificationTestFixtures.context());

        assertThat(qualification.status()).isEqualTo(QualificationStatus.QUALIFIED);
        assertThat(qualification.ruleCount()).isEqualTo(1);
    }

    @Test
    void evaluatesAnExplicitRuleSet() {
        QualificationRule qualificationRule = new QualificationRules.SessionOpen("SESSION_OPEN");

        TradeQualification qualification = engine().evaluate(
                QualificationTestFixtures.context(), List.of(qualificationRule));

        assertThat(qualification.status()).isEqualTo(QualificationStatus.QUALIFIED);
        assertThat(qualification.ruleCount()).isEqualTo(1);
    }

    @Test
    void rejectsAnEmptyRuleSet() {
        assertThatThrownBy(() -> engine().evaluate(QualificationTestFixtures.context(), List.of()))
                .isInstanceOf(QualificationValidationException.class)
                .hasMessageContaining("rules");
    }

    @Test
    void rejectsDuplicateRuleIds() {
        List<QualificationRule> rules = List.of(new FixedRule("A", RuleStatus.PASSED),
                new FixedRule("A", RuleStatus.PASSED));

        assertThatThrownBy(() -> engine().evaluate(QualificationTestFixtures.context(), rules))
                .isInstanceOf(QualificationValidationException.class)
                .hasMessageContaining("duplicate rule id A");
    }

    @Test
    void rejectsMoreRulesThanConfigured() {
        TradeQualificationConfig config = TradeQualificationConfig.builder().maxRules(2).build();

        assertThatThrownBy(() -> engine(config).evaluate(QualificationTestFixtures.context(),
                List.of(new FixedRule("A", RuleStatus.PASSED),
                        new FixedRule("B", RuleStatus.PASSED),
                        new FixedRule("C", RuleStatus.PASSED))))
                .isInstanceOf(QualificationValidationException.class)
                .hasMessageContaining("maxRules");
    }

    @Test
    void rejectsANullContext() {
        assertThatThrownBy(() -> engine().evaluate((TradeQualificationContext) null))
                .isInstanceOf(QualificationValidationException.class)
                .hasMessageContaining("context");
    }

    @Test
    void rejectsANullRuleSet() {
        assertThatThrownBy(() -> engine().evaluate(QualificationTestFixtures.context(),
                (List<QualificationRule>) null))
                .isInstanceOf(QualificationValidationException.class)
                .hasMessageContaining("rules");
    }

    @Test
    void returnsImmutableRuleEvaluations() {
        TradeQualification qualification = engine().evaluate(QualificationTestFixtures.context());

        assertThatThrownBy(() -> qualification.ruleEvaluations()
                .add(new QualificationRuleEvaluation("x", RuleStatus.PASSED, "x", Map.of())))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static TradeQualificationEngine engine() {
        return engine(TradeQualificationConfig.DEFAULT);
    }

    private static TradeQualificationEngine engine(TradeQualificationConfig config,
                                                   QualificationRule... rules) {
        List<QualificationRule> ruleList = rules.length == 0
                ? BuiltInQualificationRules.all(config, QualificationTestFixtures.ledger())
                : List.of(rules);
        return new TradeQualificationEngine(config,
                new QualificationRuleRegistry(config, ruleList), Runnable::run);
    }

    private record FixedRule(String id, RuleStatus status) implements QualificationRule {

        @Override
        public QualificationRuleEvaluation evaluate(TradeQualificationContext context) {
            return switch (status) {
                case PASSED -> QualificationRuleEvaluation.passed(id, "fixed pass", Map.of());
                case FAILED -> QualificationRuleEvaluation.failed(id, "fixed failure", Map.of());
                case INSUFFICIENT_DATA -> QualificationRuleEvaluation.insufficient(id,
                        "fixed insufficiency");
                case UNKNOWN -> QualificationRuleEvaluation.unknown(id, "fixed unknown");
            };
        }
    }

    private record ThrowingRule(String id) implements QualificationRule {

        @Override
        public QualificationRuleEvaluation evaluate(TradeQualificationContext context) {
            throw new IllegalStateException("boom");
        }
    }
}
