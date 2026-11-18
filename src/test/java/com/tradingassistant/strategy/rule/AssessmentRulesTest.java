package com.tradingassistant.strategy.rule;

import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.intelligence.domain.StructureAssessment;
import com.tradingassistant.intelligence.domain.StructureBias;
import com.tradingassistant.intelligence.domain.StructureSignal;
import com.tradingassistant.intelligence.domain.VolatilityAssessment;
import com.tradingassistant.intelligence.domain.VolatilityState;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.strategy.StrategyTestFixtures;
import com.tradingassistant.strategy.domain.EvaluationStatus;
import com.tradingassistant.strategy.domain.StrategyEvaluationContext;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssessmentRulesTest {

    @Test
    void structureSignalRuleDescribesThePresenceOfTheSignal() {
        var rule = new AssessmentRules.StructureSignalPresent("structure.breakout",
                StructureSignal.BREAKOUT_CANDIDATE);
        StrategyEvaluationContext present = new StrategyEvaluationContext(
                StrategyTestFixtures.marketAssessment(VolatilityState.STABLE,
                        Set.of(StructureSignal.BREAKOUT_CANDIDATE)),
                Optional.empty(), Optional.empty(), java.util.Map.of());
        StrategyEvaluationContext absent = StrategyEvaluationContext.of(
                StrategyTestFixtures.marketAssessment());

        assertThat(rule.evaluate(present).status()).isEqualTo(EvaluationStatus.PASSED);
        assertThat(rule.evaluate(absent).status()).isEqualTo(EvaluationStatus.FAILED);
    }

    @Test
    void structureSignalRuleReportsInsufficientStructure() {
        var market = StrategyTestFixtures.marketAssessment();
        var unavailable = new com.tradingassistant.intelligence.domain.MarketAssessment(
                market.instrument(), market.interval(), market.timestamp(), market.status(),
                market.warmup(), market.confidence(), market.trend(), market.momentum(),
                market.volatility(), market.participation(),
                new StructureAssessment(StructureBias.UNKNOWN, Set.of(),
                        AssessmentStatus.INSUFFICIENT_DATA, "unavailable"),
                market.session());
        var rule = new AssessmentRules.StructureSignalPresent("structure.breakout",
                StructureSignal.BREAKOUT_CANDIDATE);

        assertThat(rule.evaluate(StrategyEvaluationContext.of(unavailable)).status())
                .isEqualTo(EvaluationStatus.INSUFFICIENT_DATA);
    }

    @Test
    void volatilityStateRuleComparesTheDescribedState() {
        var rule = new AssessmentRules.MarketVolatilityState("volatility.expanding",
                VolatilityState.EXPANDING);
        StrategyEvaluationContext expanding = StrategyEvaluationContext.of(
                StrategyTestFixtures.marketAssessment(VolatilityState.EXPANDING, Set.of()));
        StrategyEvaluationContext stable = StrategyEvaluationContext.of(
                StrategyTestFixtures.marketAssessment());

        assertThat(rule.evaluate(expanding).status()).isEqualTo(EvaluationStatus.PASSED);
        assertThat(rule.evaluate(stable).status()).isEqualTo(EvaluationStatus.FAILED);
    }

    @Test
    void volatilityStateRuleReportsUnavailableVolatility() {
        var market = StrategyTestFixtures.marketAssessment();
        var unavailable = new com.tradingassistant.intelligence.domain.MarketAssessment(
                market.instrument(), market.interval(), market.timestamp(), market.status(),
                market.warmup(), market.confidence(), market.trend(), market.momentum(),
                new VolatilityAssessment(VolatilityState.UNKNOWN,
                        AssessmentStatus.INSUFFICIENT_DATA, Set.of(), "unavailable"),
                market.participation(), market.structure(), market.session());
        var rule = new AssessmentRules.MarketVolatilityState("volatility.expanding",
                VolatilityState.EXPANDING);

        assertThat(rule.evaluate(StrategyEvaluationContext.of(unavailable)).status())
                .isEqualTo(EvaluationStatus.INSUFFICIENT_DATA);
    }

    @Test
    void volumeRuleComparesTheCandleVolume() {
        var rule = new AssessmentRules.VolumeAtLeast("volume.at-least", 100_000L);

        assertThat(rule.evaluate(StrategyTestFixtures.context("105", 150_000L)).status())
                .isEqualTo(EvaluationStatus.PASSED);
        assertThat(rule.evaluate(StrategyTestFixtures.context("105", 10L)).status())
                .isEqualTo(EvaluationStatus.FAILED);
        assertThat(rule.evaluate(new StrategyEvaluationContext(
                StrategyTestFixtures.marketAssessment(), Optional.empty(), Optional.empty(),
                java.util.Map.of())).status())
                .isEqualTo(EvaluationStatus.INSUFFICIENT_DATA);
    }

    @Test
    void volumeRuleRejectsANegativeMinimum() {
        assertThatThrownBy(() -> new AssessmentRules.VolumeAtLeast("volume", -1L))
                .isInstanceOf(com.tradingassistant.strategy.exception
                        .StrategyValidationException.class)
                .hasMessageContaining("minimumVolume");
    }

    @Test
    void assessmentRulesReadTheAssessedInterval() {
        var rule = new AssessmentRules.MarketVolatilityState("volatility.expanding",
                VolatilityState.EXPANDING);

        assertThat(StrategyTestFixtures.marketAssessment().interval())
                .isEqualTo(Interval.ONE_MINUTE);
        assertThat(rule.evaluate(StrategyEvaluationContext.of(
                StrategyTestFixtures.marketAssessment(VolatilityState.EXPANDING, Set.of())))
                .status()).isEqualTo(EvaluationStatus.PASSED);
    }
}
