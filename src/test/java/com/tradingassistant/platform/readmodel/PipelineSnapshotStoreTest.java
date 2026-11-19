package com.tradingassistant.platform.readmodel;

import com.tradingassistant.execution.ExecutionTestFixtures;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.pipeline.event.MarketAssessmentGeneratedEvent;
import com.tradingassistant.pipeline.event.OptionsAssessmentGeneratedEvent;
import com.tradingassistant.pipeline.event.PortfolioUpdatedEvent;
import com.tradingassistant.pipeline.event.RiskAssessmentGeneratedEvent;
import com.tradingassistant.pipeline.event.StrategyEvaluatedEvent;
import com.tradingassistant.pipeline.event.TradeQualifiedEvent;
import com.tradingassistant.platform.PlatformTestFixtures;
import com.tradingassistant.risk.RiskTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineSnapshotStoreTest {

    private final PipelineSnapshotStore store = new PipelineSnapshotStore();

    @Test
    void marketAssessmentIsProjected() {
        var assessment = RiskTestFixtures.marketAssessment();

        store.onEvent(new MarketAssessmentGeneratedEvent(assessment,
                PlatformTestFixtures.NOW));

        assertThat(store.marketAssessments(10)).containsExactly(assessment);
        assertThat(store.marketAssessment(assessment.instrument(),
                assessment.interval())).contains(assessment);
    }

    @Test
    void optionsAssessmentIsProjectedBySpotInstrument() {
        var assessment = RiskTestFixtures.optionsAssessment();

        store.onEvent(new OptionsAssessmentGeneratedEvent(assessment,
                PlatformTestFixtures.NOW));

        assertThat(store.optionsAssessments(10)).containsExactly(assessment);
        assertThat(store.optionsAssessment(assessment.spotInstrument()))
                .contains(assessment);
    }

    @Test
    void strategyEvaluationIsProjected() {
        var evaluation = RiskTestFixtures.strategyEvaluation();

        store.onEvent(new StrategyEvaluatedEvent(List.of(evaluation),
                PlatformTestFixtures.NOW));

        assertThat(store.strategyEvaluations(10)).containsExactly(evaluation);
    }

    @Test
    void riskAssessmentIsProjected() {
        var assessment = ExecutionTestFixtures.assessment();

        store.onEvent(new RiskAssessmentGeneratedEvent(List.of(assessment),
                PlatformTestFixtures.NOW));

        assertThat(store.riskAssessments(10)).containsExactly(assessment);
    }

    @Test
    void qualificationIsProjected() {
        var qualification = RiskTestFixtures.qualification();

        store.onEvent(new TradeQualifiedEvent(List.of(qualification),
                PlatformTestFixtures.NOW));

        assertThat(store.qualifications(10)).containsExactly(qualification);
    }

    @Test
    void unrelatedEventsAreIgnored() {
        store.onEvent(new PortfolioUpdatedEvent(PlatformTestFixtures.portfolio(),
                PlatformTestFixtures.NOW));

        assertThat(store.size()).isZero();
    }

    @Test
    void latestDescriptionsAreReturnedFirst() {
        InstrumentKey olderInstrument = new InstrumentKey(Segment.NSE_CASH, "OLDER");
        InstrumentKey newerInstrument = new InstrumentKey(Segment.NSE_CASH, "NEWER");
        var older = RiskTestFixtures.marketAssessment(olderInstrument,
                PlatformTestFixtures.NOW, com.tradingassistant.market.domain.Interval.ONE_MINUTE,
                com.tradingassistant.intelligence.domain.VolatilityState.STABLE);
        var newer = RiskTestFixtures.marketAssessment(newerInstrument,
                PlatformTestFixtures.NOW.plusSeconds(60),
                com.tradingassistant.market.domain.Interval.ONE_MINUTE,
                com.tradingassistant.intelligence.domain.VolatilityState.STABLE);
        store.onEvent(new MarketAssessmentGeneratedEvent(older,
                PlatformTestFixtures.NOW));
        store.onEvent(new MarketAssessmentGeneratedEvent(newer,
                PlatformTestFixtures.NOW.plusSeconds(60)));

        assertThat(store.marketAssessments(10)).containsExactly(newer, older);
    }

    @Test
    void projectionsAreBounded() {
        for (int i = 0; i < PipelineSnapshotStore.CAPACITY + 5; i++) {
            InstrumentKey instrument = new InstrumentKey(Segment.NSE_CASH, "T" + i);
            store.onEvent(new MarketAssessmentGeneratedEvent(
                    RiskTestFixtures.marketAssessment(instrument),
                    PlatformTestFixtures.NOW.plusSeconds(i)));
        }

        assertThat(store.size()).isEqualTo(PipelineSnapshotStore.CAPACITY);
    }

    @Test
    void trimRemovesDescriptionsOlderThanTheCutoff() {
        InstrumentKey oldInstrument = new InstrumentKey(Segment.NSE_CASH, "OLD");
        InstrumentKey newInstrument = new InstrumentKey(Segment.NSE_CASH, "NEW");
        store.onEvent(new MarketAssessmentGeneratedEvent(
                RiskTestFixtures.marketAssessment(oldInstrument,
                        PlatformTestFixtures.NOW,
                        com.tradingassistant.market.domain.Interval.ONE_MINUTE,
                        com.tradingassistant.intelligence.domain.VolatilityState.STABLE),
                PlatformTestFixtures.NOW));
        store.onEvent(new MarketAssessmentGeneratedEvent(
                RiskTestFixtures.marketAssessment(newInstrument,
                        PlatformTestFixtures.NOW.plusSeconds(3600),
                        com.tradingassistant.market.domain.Interval.ONE_MINUTE,
                        com.tradingassistant.intelligence.domain.VolatilityState.STABLE),
                PlatformTestFixtures.NOW.plusSeconds(3600)));

        assertThat(store.trim(PlatformTestFixtures.NOW.plusSeconds(1800))).isEqualTo(1);
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void clearRemovesEveryProjection() {
        store.onEvent(new MarketAssessmentGeneratedEvent(
                RiskTestFixtures.marketAssessment(), PlatformTestFixtures.NOW));

        store.clear();

        assertThat(store.size()).isZero();
    }

    @Test
    void limitIsClampedToTheSupportedRange() {
        store.onEvent(new MarketAssessmentGeneratedEvent(
                RiskTestFixtures.marketAssessment(), PlatformTestFixtures.NOW));

        assertThat(store.marketAssessments(0)).hasSize(1);
        assertThat(store.marketAssessments(1_000)).hasSize(1);
    }
}
