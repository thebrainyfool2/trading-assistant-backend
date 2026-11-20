package com.tradingassistant.monitoring.engine;

import com.tradingassistant.intelligence.domain.MomentumState;
import com.tradingassistant.intelligence.domain.ParticipationState;
import com.tradingassistant.intelligence.domain.StructureBias;
import com.tradingassistant.intelligence.domain.StructureSignal;
import com.tradingassistant.intelligence.domain.TrendState;
import com.tradingassistant.monitoring.MonitoringTestFixtures;
import com.tradingassistant.monitoring.domain.ExitRecommendation;
import com.tradingassistant.monitoring.domain.MonitoringAlertType;
import com.tradingassistant.monitoring.domain.MonitoringState;
import com.tradingassistant.monitoring.domain.TradeMonitoringResult;
import com.tradingassistant.recommendation.RecommendationTestFixtures;
import com.tradingassistant.recommendation.domain.RecommendationType;
import com.tradingassistant.recommendation.domain.ScorerId;
import com.tradingassistant.risk.domain.RiskAssessment;
import com.tradingassistant.risk.domain.RiskStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TradeMonitoringEngineTest {

    private final TradeMonitoringEngine engine = new TradeMonitoringEngine(
            MonitoringTestFixtures.config());

    @Test
    void healthyTradeStaysHeldWithZeroDeltas() {
        TradeMonitoringResult result = engine.evaluate(MonitoringTestFixtures.context(
                MonitoringTestFixtures.callTrade(), MonitoringTestFixtures.defaultCall()));

        assertThat(result.monitoringState()).isEqualTo(MonitoringState.HEALTHY);
        assertThat(result.exitRecommendation()).isEqualTo(ExitRecommendation.HOLD);
        assertThat(result.confidenceDelta()).isEqualByComparingTo("0");
        assertThat(result.directionalEdgeDelta()).isEqualByComparingTo("0");
        assertThat(result.componentChanges()).isEmpty();
        assertThat(result.currentDirectionalEdge()).isEqualByComparingTo("30");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void confidenceDeteriorationWeakensTheTrade() {
        TradeMonitoringResult previous = engine.evaluate(MonitoringTestFixtures.context(
                MonitoringTestFixtures.callTrade(), MonitoringTestFixtures.defaultCall()));
        TradeMonitoringResult current = engine.evaluate(
                MonitoringTestFixtures.withPrevious(MonitoringTestFixtures.callTrade(),
                        MonitoringTestFixtures.recommendation(RecommendationType.BUY_CALL,
                                "60", MonitoringTestFixtures.scores("70"),
                                new BigDecimal("30")), previous));

        assertThat(current.monitoringState()).isEqualTo(MonitoringState.WEAKENING);
        assertThat(current.exitRecommendation()).isEqualTo(ExitRecommendation.WATCH_CLOSELY);
        assertThat(current.confidenceDelta()).isEqualByComparingTo("-12");
        assertThat(current.warnings()).extracting(warning -> warning.type())
                .contains(MonitoringAlertType.CONFIDENCE_DETERIORATION);
    }

    @Test
    void multipleWeakenedComponentsRaiseTheTradeToAtRisk() {
        TradeMonitoringResult previous = engine.evaluate(MonitoringTestFixtures.context(
                MonitoringTestFixtures.callTrade(), MonitoringTestFixtures.defaultCall()));
        TradeMonitoringResult current = engine.evaluate(
                MonitoringTestFixtures.withPrevious(MonitoringTestFixtures.callTrade(),
                        MonitoringTestFixtures.weakCall(), previous));

        assertThat(current.monitoringState()).isEqualTo(MonitoringState.AT_RISK);
        assertThat(current.exitRecommendation()).isEqualTo(ExitRecommendation.PREPARE_EXIT);
        assertThat(current.componentChanges()).hasSize(3);
        assertThat(current.warnings()).extracting(warning -> warning.type())
                .contains(MonitoringAlertType.COMPONENT_DETERIORATION);
    }

    @Test
    void confidenceAtOrBelowExitFloorProducesExitNow() {
        TradeMonitoringResult result = engine.evaluate(MonitoringTestFixtures.context(
                MonitoringTestFixtures.callTrade(),
                MonitoringTestFixtures.recommendation(RecommendationType.BUY_CALL, "40")));

        assertThat(result.monitoringState()).isEqualTo(MonitoringState.EXIT_RECOMMENDED);
        assertThat(result.exitRecommendation()).isEqualTo(ExitRecommendation.EXIT_NOW);
    }

    @Test
    void qualificationLossAndRiskBreachProduceExitNow() {
        TradeMonitoringResult qualificationLost = engine.evaluate(
                MonitoringTestFixtures.context(MonitoringTestFixtures.callTrade(),
                        MonitoringTestFixtures.defaultCall(),
                        RecommendationTestFixtures.bullishMarket(),
                        RecommendationTestFixtures.bullishOptions(),
                        RecommendationTestFixtures.disqualified(),
                        RecommendationTestFixtures.risk(),
                        MonitoringTestFixtures.freshness(), Optional.empty()));
        RiskAssessment breach = mock(RiskAssessment.class);
        when(breach.withinLimits()).thenReturn(false);
        when(breach.status()).thenReturn(RiskStatus.LIMIT_BREACHED);
        when(breach.strategyId()).thenReturn(RecommendationTestFixtures.STRATEGY_ID);
        when(breach.instrument()).thenReturn(MonitoringTestFixtures.SPOT);
        when(breach.interval()).thenReturn(MonitoringTestFixtures.INTERVAL);
        when(breach.timestamp()).thenReturn(MonitoringTestFixtures.START);
        TradeMonitoringResult riskLost = engine.evaluate(MonitoringTestFixtures.context(
                MonitoringTestFixtures.callTrade(), MonitoringTestFixtures.defaultCall(),
                RecommendationTestFixtures.bullishMarket(),
                RecommendationTestFixtures.bullishOptions(),
                RecommendationTestFixtures.qualification(), breach,
                MonitoringTestFixtures.freshness(), Optional.empty()));

        assertThat(qualificationLost.exitRecommendation())
                .isEqualTo(ExitRecommendation.EXIT_NOW);
        assertThat(riskLost.exitRecommendation()).isEqualTo(ExitRecommendation.EXIT_NOW);
    }

    @Test
    void aReversedTrendOrStructureWeakensAndBothInvalidate() {
        var trendOnly = RecommendationTestFixtures.market(TrendState.BEARISH,
                MomentumState.NEUTRAL, StructureBias.UPTREND,
                ParticipationState.AVERAGE, Set.of(StructureSignal.RANGE));
        var structureOnly = RecommendationTestFixtures.market(TrendState.BULLISH,
                MomentumState.NEUTRAL, StructureBias.DOWNTREND,
                ParticipationState.AVERAGE, Set.of(StructureSignal.RANGE));
        TradeMonitoringResult trend = engine.evaluate(MonitoringTestFixtures.context(
                MonitoringTestFixtures.callTrade(), MonitoringTestFixtures.defaultCall(),
                trendOnly, RecommendationTestFixtures.bullishOptions(),
                RecommendationTestFixtures.qualification(),
                RecommendationTestFixtures.risk(), MonitoringTestFixtures.freshness(),
                Optional.empty()));
        TradeMonitoringResult structure = engine.evaluate(MonitoringTestFixtures.context(
                MonitoringTestFixtures.callTrade(), MonitoringTestFixtures.defaultCall(),
                structureOnly, RecommendationTestFixtures.bullishOptions(),
                RecommendationTestFixtures.qualification(),
                RecommendationTestFixtures.risk(), MonitoringTestFixtures.freshness(),
                Optional.empty()));
        TradeMonitoringResult both = engine.evaluate(MonitoringTestFixtures.context(
                MonitoringTestFixtures.callTrade(), MonitoringTestFixtures.defaultCall(),
                RecommendationTestFixtures.bearishMarket(),
                RecommendationTestFixtures.bullishOptions(),
                RecommendationTestFixtures.qualification(),
                RecommendationTestFixtures.risk(), MonitoringTestFixtures.freshness(),
                Optional.empty()));

        assertThat(trend.monitoringState()).isEqualTo(MonitoringState.WEAKENING);
        assertThat(structure.monitoringState()).isEqualTo(MonitoringState.WEAKENING);
        assertThat(both.monitoringState()).isEqualTo(MonitoringState.INVALIDATED);
        assertThat(both.exitRecommendation()).isEqualTo(ExitRecommendation.EXIT_NOW);
    }

    @Test
    void liquidityAndEdgeDeteriorationAreExplainable() {
        TradeMonitoringResult previous = engine.evaluate(MonitoringTestFixtures.context(
                MonitoringTestFixtures.callTrade(), MonitoringTestFixtures.defaultCall()));
        TradeMonitoringResult liquidity = engine.evaluate(
                MonitoringTestFixtures.withPrevious(MonitoringTestFixtures.callTrade(),
                        MonitoringTestFixtures.recommendation(RecommendationType.BUY_CALL,
                                "72", Map.of(ScorerId.TREND, new BigDecimal("70"),
                                        ScorerId.MOMENTUM, new BigDecimal("70"),
                                        ScorerId.LIQUIDITY, new BigDecimal("50")),
                                new BigDecimal("30")), previous));
        TradeMonitoringResult edge = engine.evaluate(
                MonitoringTestFixtures.withPrevious(MonitoringTestFixtures.callTrade(),
                        MonitoringTestFixtures.recommendation(RecommendationType.BUY_CALL,
                                "72", MonitoringTestFixtures.scores("70"),
                                new BigDecimal("10")), previous));

        assertThat(liquidity.warnings()).extracting(warning -> warning.type())
                .contains(MonitoringAlertType.LIQUIDITY_DETERIORATION);
        assertThat(edge.warnings()).extracting(warning -> warning.type())
                .contains(MonitoringAlertType.DIRECTIONAL_EDGE_DETERIORATION);
        assertThat(edge.monitoringState()).isEqualTo(MonitoringState.WEAKENING);
    }

    @Test
    void sessionCloseIsWatchCloselyRatherThanInvalidation() {
        TradeMonitoringResult result = engine.evaluate(MonitoringTestFixtures.context(
                MonitoringTestFixtures.callTrade(), MonitoringTestFixtures.defaultCall(),
                RecommendationTestFixtures.bullishMarket(),
                RecommendationTestFixtures.bullishOptions(),
                RecommendationTestFixtures.qualification(),
                RecommendationTestFixtures.risk(),
                MonitoringTestFixtures.sessionClosedFreshness(), Optional.empty()));

        assertThat(result.monitoringState()).isEqualTo(MonitoringState.WEAKENING);
        assertThat(result.exitRecommendation()).isEqualTo(ExitRecommendation.WATCH_CLOSELY);
        assertThat(result.warnings()).extracting(warning -> warning.type())
                .contains(MonitoringAlertType.SESSION_CLOSED);
    }

    @Test
    void strengtheningTradeReturnsToHealthy() {
        TradeMonitoringResult previous = engine.evaluate(MonitoringTestFixtures.context(
                MonitoringTestFixtures.callTrade(), MonitoringTestFixtures.weakCall()));
        TradeMonitoringResult current = engine.evaluate(
                MonitoringTestFixtures.withPrevious(MonitoringTestFixtures.callTrade(),
                        MonitoringTestFixtures.defaultCall(), previous));

        assertThat(previous.monitoringState()).isEqualTo(MonitoringState.WEAKENING);
        assertThat(current.monitoringState()).isEqualTo(MonitoringState.HEALTHY);
        assertThat(current.confidenceDelta()).isEqualByComparingTo("14");
        assertThat(current.componentChanges()).extracting(change -> change.delta())
                .allSatisfy(delta -> assertThat(delta).isPositive());
    }

    @Test
    void aChangedRecommendationTypeInvalidatesTheThesis() {
        TradeMonitoringResult result = engine.evaluate(MonitoringTestFixtures.context(
                MonitoringTestFixtures.callTrade(),
                MonitoringTestFixtures.recommendation(RecommendationType.BUY_PUT, "72")));

        assertThat(result.monitoringState()).isEqualTo(MonitoringState.INVALIDATED);
        assertThat(result.exitRecommendation()).isEqualTo(ExitRecommendation.EXIT_NOW);
    }

    @Test
    void noTradeInvalidatesAnActiveThesis() {
        TradeMonitoringResult result = engine.evaluate(MonitoringTestFixtures.context(
                MonitoringTestFixtures.callTrade(), MonitoringTestFixtures.noTrade()));

        assertThat(result.monitoringState()).isEqualTo(MonitoringState.INVALIDATED);
        assertThat(result.warnings()).extracting(warning -> warning.type())
                .contains(MonitoringAlertType.THESIS_INVALIDATED);
    }

    @Test
    void identicalInputsAreDeterministic() {
        TradeMonitoringResult first = engine.evaluate(MonitoringTestFixtures.context(
                MonitoringTestFixtures.callTrade(), MonitoringTestFixtures.defaultCall()));
        TradeMonitoringResult second = engine.evaluate(MonitoringTestFixtures.context(
                MonitoringTestFixtures.callTrade(), MonitoringTestFixtures.defaultCall()));

        assertThat(first).isEqualTo(second);
        assertThat(first.diagnostics()).isEqualTo(second.diagnostics());
    }
}