package com.tradingassistant.risk.engine;

import com.tradingassistant.risk.RiskTestFixtures;
import com.tradingassistant.risk.api.RiskRule;
import com.tradingassistant.risk.config.RiskEngineConfig;
import com.tradingassistant.risk.domain.RiskAssessment;
import com.tradingassistant.risk.domain.RiskContext;
import com.tradingassistant.risk.domain.RiskRuleContext;
import com.tradingassistant.risk.domain.RiskRuleEvaluation;
import com.tradingassistant.risk.domain.RiskRuleStatus;
import com.tradingassistant.risk.domain.RiskStatus;
import com.tradingassistant.risk.exception.RiskEvaluationException;
import com.tradingassistant.risk.exception.RiskValidationException;
import com.tradingassistant.risk.registry.BuiltInRiskRules;
import com.tradingassistant.risk.registry.RiskRuleRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.tradingassistant.risk.RiskTestFixtures.SPOT;
import static com.tradingassistant.risk.RiskTestFixtures.START;
import static com.tradingassistant.risk.RiskTestFixtures.STRATEGY_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiskEngineTest {

    @Test
    void reportsWithinLimitsForABaselineAssessment() {
        RiskAssessment assessment = engine().evaluate(RiskTestFixtures.context());

        assertThat(assessment.status()).isEqualTo(RiskStatus.WITHIN_LIMITS);
        assertThat(assessment.withinLimits()).isTrue();
        assertThat(assessment.ruleCount()).isEqualTo(15);
        assertThat(assessment.summary()).contains("15 of 15 boundaries held");
    }

    @Test
    void reportsALimitBreachWhenABoundaryFails() {
        RiskEngineConfig config = RiskEngineConfig.builder()
                .maximumStopDistance(new BigDecimal("8"))
                .build();

        RiskAssessment assessment = engine(config)
                .evaluate(RiskTestFixtures.context());

        assertThat(assessment.status()).isEqualTo(RiskStatus.LIMIT_BREACHED);
        assertThat(assessment.withinLimits()).isFalse();
        assertThat(assessment.rule(BuiltInRiskRules.MAXIMUM_STOP_DISTANCE)).get()
                .extracting(RiskRuleEvaluation::status)
                .isEqualTo(RiskRuleStatus.FAILED);
        assertThat(assessment.summary()).contains("breached");
    }

    @Test
    void reportsInsufficientDataWithoutAStopInStrictMode() {
        RiskAssessment assessment = engine()
                .evaluate(RiskTestFixtures.contextWithoutAtr());

        assertThat(assessment.status()).isEqualTo(RiskStatus.INSUFFICIENT_DATA);
        assertThat(assessment.summary()).contains("stop distance could not be derived");
        assertThat(assessment.ruleEvaluations())
                .extracting(RiskRuleEvaluation::status)
                .containsOnly(RiskRuleStatus.INSUFFICIENT_DATA);
    }

    @Test
    void reportsPartialWhenLenientRulesDecideSomeBoundaries() {
        RiskEngineConfig config = RiskEngineConfig.builder()
                .strictValidation(false)
                .build();

        RiskAssessment assessment = engine(config)
                .evaluate(RiskTestFixtures.contextWithoutAtr());

        assertThat(assessment.status()).isEqualTo(RiskStatus.PARTIAL);
        assertThat(assessment.rule(BuiltInRiskRules.ATR_STOP_VALIDATION)).get()
                .extracting(RiskRuleEvaluation::status)
                .isEqualTo(RiskRuleStatus.INSUFFICIENT_DATA);
        assertThat(assessment.rule(BuiltInRiskRules.QUALIFICATION_ELIGIBLE)).get()
                .extracting(RiskRuleEvaluation::status)
                .isEqualTo(RiskRuleStatus.PASSED);
    }

    @Test
    void reportsUnknownWhenAContainedFailureLeavesNoDecision() {
        RiskAssessment assessment = engine(RiskEngineConfig.DEFAULT, new ThrowingRule("BROKEN"))
                .evaluate(RiskTestFixtures.context());

        assertThat(assessment.status()).isEqualTo(RiskStatus.UNKNOWN);
        assertThat(assessment.ruleEvaluations().getFirst().status())
                .isEqualTo(RiskRuleStatus.UNKNOWN);
    }

    @Test
    void propagatesARuleFailureWhenFailFastIsEnabled() {
        RiskEngineConfig config = RiskEngineConfig.builder().failFast(true).build();

        assertThatThrownBy(() -> engine(config, new ThrowingRule("BROKEN"))
                .evaluate(RiskTestFixtures.context()))
                .isInstanceOf(RiskEvaluationException.class)
                .hasMessageContaining("BROKEN");
    }

    @Test
    void parallelEvaluationPreservesOrderAndOutcomes() {
        RiskEngineConfig config = RiskEngineConfig.builder()
                .parallelEvaluation(true)
                .build();
        RiskRuleRegistry registry = new RiskRuleRegistry(BuiltInRiskRules.all());
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            RiskAssessment parallel = new RiskEngine(config, registry, executor)
                    .evaluate(RiskTestFixtures.context());
            RiskAssessment sequential = engine().evaluate(RiskTestFixtures.context());

            assertThat(parallel.ruleEvaluations())
                    .extracting(RiskRuleEvaluation::ruleId)
                    .containsExactlyElementsOf(sequential.ruleEvaluations().stream()
                            .map(RiskRuleEvaluation::ruleId).toList());
            assertThat(parallel.status()).isEqualTo(sequential.status());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void usesTheRegistrySnapshotWhenNoExplicitRuleSetIsGiven() {
        RiskRuleRegistry registry = new RiskRuleRegistry(
                List.of(new FixedRule("ONLY", RiskRuleStatus.PASSED)));

        RiskAssessment assessment = new RiskEngine(RiskEngineConfig.DEFAULT, registry,
                Runnable::run).evaluate(RiskTestFixtures.context());

        assertThat(assessment.status()).isEqualTo(RiskStatus.WITHIN_LIMITS);
        assertThat(assessment.ruleCount()).isEqualTo(1);
    }

    @Test
    void evaluatesAnExplicitRuleSet() {
        RiskAssessment assessment = engine().evaluate(RiskTestFixtures.context(),
                List.of(new FixedRule("ONLY", RiskRuleStatus.PASSED)));

        assertThat(assessment.status()).isEqualTo(RiskStatus.WITHIN_LIMITS);
        assertThat(assessment.ruleCount()).isEqualTo(1);
    }

    @Test
    void rejectsAnEmptyRuleSet() {
        assertThatThrownBy(() -> engine().evaluate(RiskTestFixtures.context(), List.of()))
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("rules");
    }

    @Test
    void rejectsDuplicateRuleIds() {
        List<RiskRule> rules = List.of(new FixedRule("A", RiskRuleStatus.PASSED),
                new FixedRule("A", RiskRuleStatus.PASSED));

        assertThatThrownBy(() -> engine().evaluate(RiskTestFixtures.context(), rules))
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("duplicate rule id A");
    }

    @Test
    void rejectsANullContext() {
        assertThatThrownBy(() -> engine().evaluate((RiskContext) null))
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("context");
    }

    @Test
    void rejectsANullRuleSet() {
        assertThatThrownBy(() -> engine().evaluate(RiskTestFixtures.context(),
                (List<RiskRule>) null))
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("rules");
    }

    @Test
    void returnsImmutableRuleEvaluations() {
        RiskAssessment assessment = engine().evaluate(RiskTestFixtures.context());

        assertThatThrownBy(() -> assessment.ruleEvaluations().add(
                new RiskRuleEvaluation("x", RiskRuleStatus.PASSED, "x", Map.of())))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void describesRiskSizeAndValidations() {
        RiskAssessment assessment = engine().evaluate(RiskTestFixtures.context());

        assertThat(assessment.riskPerTrade()).isEqualByComparingTo("1000");
        assertThat(assessment.maximumPermittedLoss()).isEqualByComparingTo("1000");
        assertThat(assessment.stopDistance()).isEqualByComparingTo("10");
        assertThat(assessment.atrMultiple()).isEqualByComparingTo("2");
        assertThat(assessment.quantity()).isEqualByComparingTo("100");
        assertThat(assessment.positionValue()).isEqualByComparingTo("50000");
        assertThat(assessment.maximumLeverageAllowed()).isEqualByComparingTo("5");
        assertThat(assessment.dailyLoss().dailyLimit()).isEqualByComparingTo("3000");
        assertThat(assessment.exposure().maximumExposure())
                .isEqualByComparingTo("100000");
        assertThat(assessment.riskReward().achievedRatio()).get()
                .isEqualTo(new BigDecimal("2.5"));
        assertThat(assessment.diagnostics())
                .containsKeys("capital", "riskPercent", "atr", "quantity");
    }

    @Test
    void referencesTheQualificationItAssessed() {
        RiskAssessment assessment = engine().evaluate(RiskTestFixtures.context());

        assertThat(assessment.qualification().strategyId()).isEqualTo(STRATEGY_ID);
        assertThat(assessment.qualification().instrument()).isEqualTo(SPOT);
        assertThat(assessment.qualification().timestamp()).isEqualTo(START);
        assertThat(assessment.qualificationStatus())
                .isEqualTo(com.tradingassistant.qualification.domain.QualificationStatus
                        .QUALIFIED);
        assertThat(assessment.interval()).isEqualTo(
                com.tradingassistant.market.domain.Interval.ONE_MINUTE);
    }

    private static RiskEngine engine() {
        return engine(RiskEngineConfig.DEFAULT);
    }

    private static RiskEngine engine(RiskEngineConfig config, RiskRule... rules) {
        List<RiskRule> ruleList = rules.length == 0
                ? BuiltInRiskRules.all()
                : List.of(rules);
        return new RiskEngine(config, new RiskRuleRegistry(ruleList), Runnable::run);
    }

    private record FixedRule(String id, RiskRuleStatus status) implements RiskRule {

        @Override
        public RiskRuleEvaluation evaluate(RiskRuleContext context) {
            return switch (status) {
                case PASSED -> RiskRuleEvaluation.passed(id, "fixed pass", Map.of());
                case FAILED -> RiskRuleEvaluation.failed(id, "fixed failure", Map.of());
                case INSUFFICIENT_DATA -> RiskRuleEvaluation.insufficient(id,
                        "fixed insufficiency");
                case UNKNOWN -> RiskRuleEvaluation.unknown(id, "fixed unknown");
            };
        }
    }

    private record ThrowingRule(String id) implements RiskRule {

        @Override
        public RiskRuleEvaluation evaluate(RiskRuleContext context) {
            throw new IllegalStateException("boom");
        }
    }
}
