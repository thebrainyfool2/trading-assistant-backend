package com.tradingassistant.risk.rule;

import com.tradingassistant.intelligence.domain.VolatilityState;
import com.tradingassistant.qualification.domain.QualificationStatus;
import com.tradingassistant.risk.RiskTestFixtures;
import com.tradingassistant.risk.config.RiskEngineConfig;
import com.tradingassistant.risk.domain.RiskRuleContext;
import com.tradingassistant.risk.domain.RiskRuleStatus;
import com.tradingassistant.risk.exception.RiskValidationException;
import com.tradingassistant.strategy.domain.EvaluationStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.tradingassistant.risk.RiskTestFixtures.SPOT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiskRulesTest {

    private final RiskRuleContext baseline = RiskTestFixtures.ruleContext();

    @Test
    void everyBuiltInRulePassesForABaselineAssessment() {
        assertThat(new RiskRules.QualificationEligible("r").evaluate(baseline).status())
                .isEqualTo(RiskRuleStatus.PASSED);
        assertThat(new RiskRules.AtrStopValidation("r").evaluate(baseline).status())
                .isEqualTo(RiskRuleStatus.PASSED);
        assertThat(new RiskRules.MinimumStopDistance("r").evaluate(baseline).status())
                .isEqualTo(RiskRuleStatus.PASSED);
        assertThat(new RiskRules.MaximumStopDistance("r").evaluate(baseline).status())
                .isEqualTo(RiskRuleStatus.PASSED);
        assertThat(new RiskRules.VolatilityMultiplier("r").evaluate(baseline).status())
                .isEqualTo(RiskRuleStatus.PASSED);
        assertThat(new RiskRules.MaximumRiskPercent("r").evaluate(baseline).status())
                .isEqualTo(RiskRuleStatus.PASSED);
        assertThat(new RiskRules.DailyRiskLimit("r").evaluate(baseline).status())
                .isEqualTo(RiskRuleStatus.PASSED);
        assertThat(new RiskRules.SessionLossCap("r").evaluate(baseline).status())
                .isEqualTo(RiskRuleStatus.PASSED);
        assertThat(new RiskRules.MaximumOpenExposure("r").evaluate(baseline).status())
                .isEqualTo(RiskRuleStatus.PASSED);
        assertThat(new RiskRules.MaximumLeverage("r").evaluate(baseline).status())
                .isEqualTo(RiskRuleStatus.PASSED);
        assertThat(new RiskRules.MaximumQuantity("r").evaluate(baseline).status())
                .isEqualTo(RiskRuleStatus.PASSED);
        assertThat(new RiskRules.MinimumRewardRisk("r").evaluate(baseline).status())
                .isEqualTo(RiskRuleStatus.PASSED);
        assertThat(new RiskRules.GapProtection("r").evaluate(baseline).status())
                .isEqualTo(RiskRuleStatus.PASSED);
        assertThat(new RiskRules.OptionPremiumRisk("r").evaluate(baseline).status())
                .isEqualTo(RiskRuleStatus.PASSED);
        assertThat(new RiskRules.InstrumentOverride("r").evaluate(baseline).status())
                .isEqualTo(RiskRuleStatus.PASSED);
        assertThat(baseline.plan().quantity()).isEqualByComparingTo("100");
        assertThat(baseline.plan().riskAmount()).isEqualByComparingTo("1000");
    }

    @Test
    void qualificationEligibleRejectsUnqualifiedContexts() {
        var rule = new RiskRules.QualificationEligible("QUALIFICATION_ELIGIBLE");

        assertThat(rule.evaluate(RiskTestFixtures.ruleContext(
                RiskTestFixtures.context(QualificationStatus.DISQUALIFIED),
                RiskEngineConfig.DEFAULT)).status()).isEqualTo(RiskRuleStatus.FAILED);
        assertThat(rule.evaluate(RiskTestFixtures.ruleContext(
                RiskTestFixtures.context(QualificationStatus.PARTIAL),
                RiskEngineConfig.DEFAULT)).status()).isEqualTo(RiskRuleStatus.FAILED);
        assertThat(rule.evaluate(RiskTestFixtures.ruleContext(
                RiskTestFixtures.context(QualificationStatus.INSUFFICIENT_DATA),
                RiskEngineConfig.DEFAULT)).status())
                .isEqualTo(RiskRuleStatus.INSUFFICIENT_DATA);
    }

    @Test
    void atrStopValidationRequiresAPositiveAtr() {
        var rule = new RiskRules.AtrStopValidation("ATR_STOP_VALIDATION");

        assertThat(rule.evaluate(RiskTestFixtures.ruleContext(
                RiskTestFixtures.contextWithoutAtr(), RiskEngineConfig.DEFAULT)).status())
                .isEqualTo(RiskRuleStatus.INSUFFICIENT_DATA);
        assertThat(rule.evaluate(RiskTestFixtures.ruleContext(
                RiskTestFixtures.planWithAtr("0"), RiskEngineConfig.DEFAULT)).status())
                .isEqualTo(RiskRuleStatus.FAILED);
    }

    @Test
    void minimumStopDistanceRejectsATightStop() {
        RiskEngineConfig config = RiskEngineConfig.builder()
                .minimumStopDistance(new BigDecimal("20"))
                .build();

        var evaluation = new RiskRules.MinimumStopDistance("MINIMUM_STOP_DISTANCE")
                .evaluate(RiskTestFixtures.ruleContext(RiskTestFixtures.context(), config));

        assertThat(evaluation.status()).isEqualTo(RiskRuleStatus.FAILED);
        assertThat(evaluation.metadata()).containsKeys("distance", "minimum");
    }

    @Test
    void maximumStopDistanceRejectsAWideStop() {
        RiskEngineConfig config = RiskEngineConfig.builder()
                .maximumStopDistance(new BigDecimal("8"))
                .build();

        assertThat(new RiskRules.MaximumStopDistance("MAXIMUM_STOP_DISTANCE")
                .evaluate(RiskTestFixtures.ruleContext(RiskTestFixtures.context(), config))
                .status()).isEqualTo(RiskRuleStatus.FAILED);
    }

    @Test
    void stopDistanceRulesReportMissingDataWithoutAStop() {
        RiskRuleContext context = RiskTestFixtures.ruleContext(
                RiskTestFixtures.contextWithoutAtr(), RiskEngineConfig.DEFAULT);

        assertThat(new RiskRules.MinimumStopDistance("MINIMUM_STOP_DISTANCE")
                .evaluate(context).status()).isEqualTo(RiskRuleStatus.INSUFFICIENT_DATA);
        assertThat(new RiskRules.MaximumStopDistance("MAXIMUM_STOP_DISTANCE")
                .evaluate(context).status()).isEqualTo(RiskRuleStatus.INSUFFICIENT_DATA);
    }

    @Test
    void volatilityMultiplierNeedsAResolvedState() {
        var rule = new RiskRules.VolatilityMultiplier("VOLATILITY_MULTIPLIER");

        assertThat(rule.evaluate(RiskTestFixtures.ruleContext(
                RiskTestFixtures.context(VolatilityState.UNKNOWN),
                RiskEngineConfig.DEFAULT)).status())
                .isEqualTo(RiskRuleStatus.INSUFFICIENT_DATA);
        assertThat(rule.evaluate(RiskTestFixtures.ruleContext(
                RiskTestFixtures.planWithAdjustment("3"), RiskEngineConfig.DEFAULT)).status())
                .isEqualTo(RiskRuleStatus.FAILED);
    }

    @Test
    void maximumRiskPercentRejectsAPlanThatExceedsThePolicy() {
        var rule = new RiskRules.MaximumRiskPercent("MAXIMUM_RISK_PERCENT");

        var evaluation = rule.evaluate(RiskTestFixtures.ruleContext(
                RiskTestFixtures.planWithRiskPercent("2"), RiskEngineConfig.DEFAULT));

        assertThat(evaluation.status()).isEqualTo(RiskRuleStatus.FAILED);
        assertThat(evaluation.metadata()).containsKeys("riskPercent", "maximumRiskPercent");
    }

    @Test
    void dailyRiskLimitRejectsRiskAboveTheDailyLimit() {
        RiskEngineConfig config = RiskEngineConfig.builder()
                .maximumDailyLossPercent(new BigDecimal("0.5"))
                .maximumSessionLossPercent(new BigDecimal("0.4"))
                .build();

        assertThat(new RiskRules.DailyRiskLimit("DAILY_RISK_LIMIT")
                .evaluate(RiskTestFixtures.ruleContext(RiskTestFixtures.context(), config))
                .status()).isEqualTo(RiskRuleStatus.FAILED);
    }

    @Test
    void sessionLossCapRejectsRiskAboveTheSessionCap() {
        RiskEngineConfig config = RiskEngineConfig.builder()
                .maximumSessionLossPercent(new BigDecimal("0.5"))
                .build();

        assertThat(new RiskRules.SessionLossCap("SESSION_LOSS_CAP")
                .evaluate(RiskTestFixtures.ruleContext(RiskTestFixtures.context(), config))
                .status()).isEqualTo(RiskRuleStatus.FAILED);
    }

    @Test
    void maximumOpenExposureRejectsAPositionAboveTheLimit() {
        RiskEngineConfig config = RiskEngineConfig.builder()
                .maximumOpenExposurePercent(new BigDecimal("20"))
                .build();

        assertThat(new RiskRules.MaximumOpenExposure("MAXIMUM_OPEN_EXPOSURE")
                .evaluate(RiskTestFixtures.ruleContext(RiskTestFixtures.context(), config))
                .status()).isEqualTo(RiskRuleStatus.FAILED);
    }

    @Test
    void maximumLeverageRejectsLeverageAboveTheCeiling() {
        RiskEngineConfig config = RiskEngineConfig.builder()
                .maximumLeverage(new BigDecimal("0.25"))
                .build();

        assertThat(new RiskRules.MaximumLeverage("MAXIMUM_LEVERAGE")
                .evaluate(RiskTestFixtures.ruleContext(RiskTestFixtures.context(), config))
                .status()).isEqualTo(RiskRuleStatus.FAILED);
    }

    @Test
    void maximumQuantityRejectsASizeAboveTheCeiling() {
        RiskEngineConfig config = RiskEngineConfig.builder()
                .maximumQuantity(new BigDecimal("50"))
                .build();

        assertThat(new RiskRules.MaximumQuantity("MAXIMUM_QUANTITY")
                .evaluate(RiskTestFixtures.ruleContext(RiskTestFixtures.context(), config))
                .status()).isEqualTo(RiskRuleStatus.FAILED);
    }

    @Test
    void sizeDependentRulesReportMissingDataWithoutASize() {
        RiskRuleContext context = RiskTestFixtures.ruleContext(
                RiskTestFixtures.contextWithoutAtr(), RiskEngineConfig.DEFAULT);

        assertThat(new RiskRules.MaximumOpenExposure("MAXIMUM_OPEN_EXPOSURE")
                .evaluate(context).status()).isEqualTo(RiskRuleStatus.INSUFFICIENT_DATA);
        assertThat(new RiskRules.MaximumLeverage("MAXIMUM_LEVERAGE")
                .evaluate(context).status()).isEqualTo(RiskRuleStatus.INSUFFICIENT_DATA);
        assertThat(new RiskRules.MaximumQuantity("MAXIMUM_QUANTITY")
                .evaluate(context).status()).isEqualTo(RiskRuleStatus.INSUFFICIENT_DATA);
    }

    @Test
    void minimumRewardRiskComparesTheExpectedMoveWithTheStop() {
        var rule = new RiskRules.MinimumRewardRisk("MINIMUM_REWARD_RISK");

        var evaluation = rule.evaluate(RiskTestFixtures.ruleContext(
                RiskTestFixtures.context(RiskTestFixtures.optionsAssessment(
                        new BigDecimal("5"))), RiskEngineConfig.DEFAULT));

        assertThat(evaluation.status()).isEqualTo(RiskRuleStatus.FAILED);
        assertThat(evaluation.metadata()).containsKey("achievedRatio");
        assertThat(rule.evaluate(RiskTestFixtures.ruleContext(
                RiskTestFixtures.contextWithoutOptions(), RiskEngineConfig.DEFAULT)).status())
                .isEqualTo(RiskRuleStatus.INSUFFICIENT_DATA);
    }

    @Test
    void gapProtectionRejectsAStopInsideNormalNoise() {
        var rule = new RiskRules.GapProtection("GAP_PROTECTION");

        assertThat(rule.evaluate(RiskTestFixtures.ruleContext(
                RiskTestFixtures.planWithStop("5", "1", "0.2"),
                RiskEngineConfig.DEFAULT)).status()).isEqualTo(RiskRuleStatus.FAILED);
        assertThat(rule.evaluate(RiskTestFixtures.ruleContext(
                RiskTestFixtures.contextWithoutAtr(), RiskEngineConfig.DEFAULT)).status())
                .isEqualTo(RiskRuleStatus.INSUFFICIENT_DATA);
    }

    @Test
    void optionPremiumRiskComparesPlannedRiskWithTheOptionBudget() {
        var rule = new RiskRules.OptionPremiumRisk("OPTION_PREMIUM_RISK");

        var evaluation = rule.evaluate(RiskTestFixtures.ruleContext(
                RiskTestFixtures.context(RiskTestFixtures.optionsAssessment(
                        new BigDecimal("5"))), RiskEngineConfig.DEFAULT));

        assertThat(evaluation.status()).isEqualTo(RiskRuleStatus.FAILED);
        assertThat(evaluation.metadata()).containsKeys("plannedRisk", "optionRiskBudget");
        assertThat(rule.evaluate(RiskTestFixtures.ruleContext(
                RiskTestFixtures.contextWithoutOptions(), RiskEngineConfig.DEFAULT)).status())
                .isEqualTo(RiskRuleStatus.PASSED);
    }

    @Test
    void instrumentOverrideReportsTheAppliedPolicy() {
        RiskEngineConfig config = RiskEngineConfig.builder()
                .instrumentRiskPercentOverrides(Map.of(SPOT, new BigDecimal("0.5")))
                .build();
        var rule = new RiskRules.InstrumentOverride("INSTRUMENT_OVERRIDE");

        var evaluation = rule.evaluate(RiskTestFixtures.ruleContext(
                RiskTestFixtures.context(), config));

        assertThat(evaluation.status()).isEqualTo(RiskRuleStatus.PASSED);
        assertThat(evaluation.metadata()).containsEntry("override", "0.5");

        var mismatch = rule.evaluate(RiskTestFixtures.ruleContext(
                RiskTestFixtures.planWithRiskPercent("1"), config));
        assertThat(mismatch.status()).isEqualTo(RiskRuleStatus.FAILED);
    }

    @Test
    void rulesRejectBlankIdentifiersAndNullContexts() {
        assertThatThrownBy(() -> new RiskRules.MaximumLeverage(" "))
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("rule id");
        assertThatThrownBy(() -> new RiskRules.MaximumLeverage("x").evaluate(null))
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("context");
    }

    @Test
    void rulesExposeTheirIdentifiers() {
        assertThat(new RiskRules.GapProtection("gap").id()).isEqualTo("gap");
        assertThat(List.of(new RiskRules.MaximumQuantity("qty").id()))
                .containsExactly("qty");
        assertThat(new RiskRules.QualificationEligible("x")
                .evaluate(RiskTestFixtures.ruleContext(
                        RiskTestFixtures.contextWithEvaluation(
                                RiskTestFixtures.strategyEvaluation(EvaluationStatus.FAILED)),
                        RiskEngineConfig.DEFAULT)).status())
                .isEqualTo(RiskRuleStatus.PASSED);
    }
}
