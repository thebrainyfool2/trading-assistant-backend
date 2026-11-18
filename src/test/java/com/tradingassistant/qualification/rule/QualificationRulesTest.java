package com.tradingassistant.qualification.rule;

import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.intelligence.domain.ConfidenceLevel;
import com.tradingassistant.intelligence.domain.ParticipationState;
import com.tradingassistant.intelligence.domain.SessionPhase;
import com.tradingassistant.options.domain.LiquidityState;
import com.tradingassistant.qualification.QualificationTestFixtures;
import com.tradingassistant.qualification.domain.QualificationKey;
import com.tradingassistant.qualification.domain.QualificationLedger;
import com.tradingassistant.qualification.domain.RuleStatus;
import com.tradingassistant.qualification.domain.TradeQualificationContext;
import com.tradingassistant.qualification.exception.QualificationValidationException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.tradingassistant.qualification.QualificationTestFixtures.LATER;
import static com.tradingassistant.qualification.QualificationTestFixtures.SPOT;
import static com.tradingassistant.qualification.QualificationTestFixtures.START;
import static com.tradingassistant.qualification.QualificationTestFixtures.STRATEGY_ID;
import static com.tradingassistant.qualification.QualificationTestFixtures.candle;
import static com.tradingassistant.qualification.QualificationTestFixtures.context;
import static com.tradingassistant.qualification.QualificationTestFixtures.marketAssessment;
import static com.tradingassistant.qualification.QualificationTestFixtures.optionsAssessment;
import static com.tradingassistant.qualification.QualificationTestFixtures.session;
import static com.tradingassistant.qualification.QualificationTestFixtures.strategyEvaluation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QualificationRulesTest {

    @Test
    void sessionOpenPassesInsideEveryTradingPhase() {
        var rule = new QualificationRules.SessionOpen("SESSION_OPEN");

        assertThat(rule.evaluate(context(marketAssessment(SessionPhase.OPENING))).status())
                .isEqualTo(RuleStatus.PASSED);
        assertThat(rule.evaluate(context(marketAssessment(SessionPhase.REGULAR))).status())
                .isEqualTo(RuleStatus.PASSED);
        assertThat(rule.evaluate(context(marketAssessment(SessionPhase.CLOSING))).status())
                .isEqualTo(RuleStatus.PASSED);
    }

    @Test
    void sessionOpenRejectsPreOpenAfterMarketAndUnknownPhases() {
        var rule = new QualificationRules.SessionOpen("SESSION_OPEN");

        assertThat(rule.evaluate(context(marketAssessment(SessionPhase.PRE_OPEN))).status())
                .isEqualTo(RuleStatus.FAILED);
        assertThat(rule.evaluate(context(marketAssessment(SessionPhase.AFTER_MARKET))).status())
                .isEqualTo(RuleStatus.FAILED);
        assertThat(rule.evaluate(context(marketAssessment(SessionPhase.UNKNOWN))).status())
                .isEqualTo(RuleStatus.FAILED);
    }

    @Test
    void sessionOpenReportsInsufficientDataWhenTheCalendarIsNotReady() {
        TradeQualificationContext context = new TradeQualificationContext(
                strategyEvaluation(), marketAssessment(), Optional.empty(),
                session(SessionPhase.REGULAR, AssessmentStatus.INSUFFICIENT_DATA),
                Optional.of(candle()));

        var evaluation = new QualificationRules.SessionOpen("SESSION_OPEN").evaluate(context);

        assertThat(evaluation.status()).isEqualTo(RuleStatus.INSUFFICIENT_DATA);
        assertThat(evaluation.reason()).contains("INSUFFICIENT_DATA");
    }

    @Test
    void marketAssessmentReadyPassesOnlyForAReadyAssessment() {
        var rule = new QualificationRules.MarketAssessmentReady("MARKET_ASSESSMENT_READY");

        assertThat(rule.evaluate(context()).status()).isEqualTo(RuleStatus.PASSED);
        assertThat(rule.evaluate(context(marketAssessment(AssessmentStatus.PARTIAL,
                ConfidenceLevel.HIGH, ParticipationState.AVERAGE, SessionPhase.REGULAR)))
                .status()).isEqualTo(RuleStatus.FAILED);
        assertThat(rule.evaluate(context(marketAssessment(AssessmentStatus.INSUFFICIENT_DATA,
                ConfidenceLevel.HIGH, ParticipationState.AVERAGE, SessionPhase.REGULAR)))
                .status()).isEqualTo(RuleStatus.INSUFFICIENT_DATA);
    }

    @Test
    void optionsRequirementPassesWhenAnAssessmentIsNotRequired() {
        var rule = new QualificationRules.OptionsRequirement("OPTIONS_REQUIRED", false);

        assertThat(rule.evaluate(context()).status()).isEqualTo(RuleStatus.PASSED);
        assertThat(rule.evaluate(context(optionsAssessment())).status())
                .isEqualTo(RuleStatus.PASSED);
    }

    @Test
    void optionsRequirementReportsInsufficientDataWhenRequiredButAbsent() {
        var rule = new QualificationRules.OptionsRequirement("OPTIONS_REQUIRED", true);

        var evaluation = rule.evaluate(context());

        assertThat(evaluation.status()).isEqualTo(RuleStatus.INSUFFICIENT_DATA);
        assertThat(evaluation.reason()).contains("required");
    }

    @Test
    void optionsRequirementFailsWhenAPresentAssessmentIsNotReady() {
        var rule = new QualificationRules.OptionsRequirement("OPTIONS_REQUIRED", true);

        var evaluation = rule.evaluate(context(optionsAssessment(AssessmentStatus.PARTIAL)));

        assertThat(evaluation.passed()).isFalse();
        assertThat(evaluation.status()).isEqualTo(RuleStatus.FAILED);
        assertThat(evaluation.reason()).contains("PARTIAL");
    }

    @Test
    void optionsFreshnessPassesForAnAlignedAssessment() {
        var rule = new QualificationRules.OptionsFreshness("OPTIONS_FRESH", false);

        assertThat(rule.evaluate(context(optionsAssessment(START))).status())
                .isEqualTo(RuleStatus.PASSED);
    }

    @Test
    void optionsFreshnessFailsForAnAssessmentOfAnotherInstant() {
        var rule = new QualificationRules.OptionsFreshness("OPTIONS_FRESH", false);

        var evaluation = rule.evaluate(context(optionsAssessment(LATER)));

        assertThat(evaluation.status()).isEqualTo(RuleStatus.FAILED);
        assertThat(evaluation.metadata()).containsKeys("optionsTimestamp", "timestamp");
    }

    @Test
    void optionsFreshnessReportsInsufficientDataWhenRequiredButAbsent() {
        var rule = new QualificationRules.OptionsFreshness("OPTIONS_FRESH", true);

        assertThat(rule.evaluate(context()).status()).isEqualTo(RuleStatus.INSUFFICIENT_DATA);
        assertThat(new QualificationRules.OptionsFreshness("OPTIONS_FRESH", false)
                .evaluate(context()).status()).isEqualTo(RuleStatus.PASSED);
    }

    @Test
    void sessionAlignmentPassesWhenBothAssessmentsShareThePhase() {
        var rule = new QualificationRules.SessionAlignment("SESSION_ALIGNED", false);

        assertThat(rule.evaluate(context(optionsAssessment())).status())
                .isEqualTo(RuleStatus.PASSED);
    }

    @Test
    void sessionAlignmentFailsWhenThePhasesDiffer() {
        var rule = new QualificationRules.SessionAlignment("SESSION_ALIGNED", false);

        var evaluation = rule.evaluate(context(optionsAssessment(SessionPhase.CLOSING)));

        assertThat(evaluation.status()).isEqualTo(RuleStatus.FAILED);
        assertThat(evaluation.metadata()).containsEntry("optionsPhase", "CLOSING");
    }

    @Test
    void sessionAlignmentReportsInsufficientDataWhenTheOptionSessionIsNotReady() {
        var rule = new QualificationRules.SessionAlignment("SESSION_ALIGNED", false);
        var options = optionsAssessment(START,
                session(SessionPhase.REGULAR, AssessmentStatus.INSUFFICIENT_DATA),
                LiquidityState.LIQUID, AssessmentStatus.READY);

        assertThat(rule.evaluate(context(options)).status())
                .isEqualTo(RuleStatus.INSUFFICIENT_DATA);
    }

    @Test
    void strategyStatusKnownRejectsOnlyTheUnknownState() {
        var rule = new QualificationRules.StrategyStatusKnown("STRATEGY_STATUS_KNOWN");

        assertThat(rule.evaluate(context()).status()).isEqualTo(RuleStatus.PASSED);
        assertThat(rule.evaluate(context(com.tradingassistant.strategy.domain.EvaluationStatus
                .UNKNOWN)).status()).isEqualTo(RuleStatus.FAILED);
    }

    @Test
    void strategyStatusPassedAcceptsOnlyAPassedEvaluation() {
        var rule = new QualificationRules.StrategyStatusPassed("STRATEGY_STATUS_PASSED");

        assertThat(rule.evaluate(context()).status()).isEqualTo(RuleStatus.PASSED);
        assertThat(rule.evaluate(context(com.tradingassistant.strategy.domain.EvaluationStatus
                .FAILED)).status()).isEqualTo(RuleStatus.FAILED);
        assertThat(rule.evaluate(context(com.tradingassistant.strategy.domain.EvaluationStatus
                .PARTIAL)).status()).isEqualTo(RuleStatus.FAILED);
        assertThat(rule.evaluate(context(com.tradingassistant.strategy.domain.EvaluationStatus
                .INSUFFICIENT_DATA)).status()).isEqualTo(RuleStatus.INSUFFICIENT_DATA);
    }

    @Test
    void minimumMarketConfidenceComparesAgainstTheConfiguredFloor() {
        var rule = new QualificationRules.MinimumMarketConfidence("MARKET_CONFIDENCE",
                ConfidenceLevel.HIGH);

        assertThat(rule.evaluate(context()).status()).isEqualTo(RuleStatus.PASSED);
        assertThat(rule.evaluate(context(marketAssessment(ConfidenceLevel.MEDIUM))).status())
                .isEqualTo(RuleStatus.FAILED);
        assertThat(rule.evaluate(context(marketAssessment(AssessmentStatus.READY,
                ConfidenceLevel.UNKNOWN, ParticipationState.AVERAGE, SessionPhase.REGULAR)))
                .status()).isEqualTo(RuleStatus.INSUFFICIENT_DATA);
    }

    @Test
    void minimumMarketConfidenceAcceptsALowerConfiguredFloor() {
        var rule = new QualificationRules.MinimumMarketConfidence("MARKET_CONFIDENCE",
                ConfidenceLevel.LOW);

        assertThat(rule.evaluate(context(marketAssessment(ConfidenceLevel.MEDIUM))).status())
                .isEqualTo(RuleStatus.PASSED);
    }

    @Test
    void minimumParticipationRejectsWeakerStates() {
        var rule = new QualificationRules.MinimumParticipation("MARKET_PARTICIPATION",
                ParticipationState.AVERAGE);

        assertThat(rule.evaluate(context()).status()).isEqualTo(RuleStatus.PASSED);
        assertThat(rule.evaluate(context(marketAssessment(ParticipationState.STRONG))).status())
                .isEqualTo(RuleStatus.PASSED);
        assertThat(rule.evaluate(context(marketAssessment(ParticipationState.WEAK))).status())
                .isEqualTo(RuleStatus.FAILED);
        assertThat(rule.evaluate(context(marketAssessment(ParticipationState.UNKNOWN))).status())
                .isEqualTo(RuleStatus.INSUFFICIENT_DATA);
    }

    @Test
    void optionLiquidityRejectsIlliquidAndUnknownStates() {
        var rule = new QualificationRules.OptionLiquidity("OPTION_LIQUIDITY", false);

        assertThat(rule.evaluate(context(optionsAssessment(LiquidityState.LIQUID))).status())
                .isEqualTo(RuleStatus.PASSED);
        assertThat(rule.evaluate(context(optionsAssessment(LiquidityState.MODERATE))).status())
                .isEqualTo(RuleStatus.PASSED);
        assertThat(rule.evaluate(context(optionsAssessment(LiquidityState.ILLIQUID))).status())
                .isEqualTo(RuleStatus.FAILED);
        assertThat(rule.evaluate(context(optionsAssessment(LiquidityState.UNKNOWN))).status())
                .isEqualTo(RuleStatus.INSUFFICIENT_DATA);
    }

    @Test
    void optionLiquidityIsOptionalUnlessConfiguredAsRequired() {
        assertThat(new QualificationRules.OptionLiquidity("OPTION_LIQUIDITY", false)
                .evaluate(context()).status()).isEqualTo(RuleStatus.PASSED);
        assertThat(new QualificationRules.OptionLiquidity("OPTION_LIQUIDITY", true)
                .evaluate(context()).status()).isEqualTo(RuleStatus.INSUFFICIENT_DATA);
    }

    @Test
    void duplicateQualificationPassesOnlyUntilTheKeyIsRecorded() {
        QualificationLedger ledger = QualificationTestFixtures.ledger();
        var rule = new QualificationRules.DuplicateQualification("DUPLICATE_QUALIFICATION", ledger);

        assertThat(rule.evaluate(context()).status()).isEqualTo(RuleStatus.PASSED);

        ledger.record(new QualificationKey(STRATEGY_ID, SPOT, START));

        var evaluation = rule.evaluate(context());
        assertThat(evaluation.status()).isEqualTo(RuleStatus.FAILED);
        assertThat(evaluation.reason()).contains("already qualified");
    }

    @Test
    void rulesRejectBlankIdentifiers() {
        assertThatThrownBy(() -> new QualificationRules.SessionOpen(" "))
                .isInstanceOf(QualificationValidationException.class)
                .hasMessageContaining("rule id");
        assertThatThrownBy(() -> new QualificationRules.DuplicateQualification("x", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rulesExposeTheirIdentifiers() {
        assertThat(new QualificationRules.SessionOpen("a").id()).isEqualTo("a");
        assertThat(new QualificationRules.MarketAssessmentReady("b").id()).isEqualTo("b");
    }
}
