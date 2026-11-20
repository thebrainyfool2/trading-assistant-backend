package com.tradingassistant.platform.trade;

import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.monitoring.MonitoringTestFixtures;
import com.tradingassistant.monitoring.domain.ActiveTradeMonitoring;
import com.tradingassistant.monitoring.domain.ManualTrade;
import com.tradingassistant.monitoring.service.MonitoringQueryService;
import com.tradingassistant.monitoring.service.MonitoringService;
import com.tradingassistant.platform.error.PlatformException;
import com.tradingassistant.recommendation.RecommendationTestFixtures;
import com.tradingassistant.recommendation.service.RecommendationService;
import com.tradingassistant.risk.config.RiskEngineConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManualTradeCommandServiceTest {

    private final MonitoringService monitoring = mock(MonitoringService.class);
    private final MonitoringQueryService queries = mock(MonitoringQueryService.class);
    private final RecommendationService recommendations = mock(RecommendationService.class);
    private final TradePlanConfig config = TradePlanConfig.defaults();
    private final TradePlanService plans = new TradePlanService(
            RiskEngineConfig.DEFAULT, config,
            Clock.fixed(RecommendationTestFixtures.START, ZoneOffset.UTC));
    private final ManualTradeCommandService service = new ManualTradeCommandService(
            monitoring, queries, recommendations, plans, config,
            Clock.fixed(RecommendationTestFixtures.START, ZoneOffset.UTC));

    @Test
    void recordsTheOwnersFillAndUsesExplicitBoundaries() {
        ManualTrade fixture = MonitoringTestFixtures.callTrade();
        when(recommendations.at(any(), any(), any())).thenReturn(
                Optional.of(MonitoringTestFixtures.defaultCall()));
        when(recommendations.contextAt(any(), any(), any())).thenReturn(
                Optional.of(RecommendationTestFixtures.bullishContext()));
        when(queries.require("trade-1")).thenReturn(
                new ActiveTradeMonitoring(fixture, Optional.empty()));

        var view = service.register(request("BUY_CALL", "NIFTY-CE-500",
                new BigDecimal("140"), new BigDecimal("80")));

        ArgumentCaptor<ManualTrade> captured = ArgumentCaptor.forClass(ManualTrade.class);
        verify(monitoring).register(captured.capture());
        assertThat(view.tradeId()).isEqualTo("trade-1");
        assertThat(captured.getValue().entryPrice()).isEqualByComparingTo("100");
        assertThat(captured.getValue().targetPremium()).contains(new BigDecimal("140.00"));
        assertThat(captured.getValue().stopPremium()).contains(new BigDecimal("80.00"));
        assertThat(captured.getValue().recommendationTimestamp())
                .contains(RecommendationTestFixtures.START);
    }

    @Test
    void derivesBoundariesFromThePinnedPlanWhenTheOwnerOmitsThem() {
        ManualTrade fixture = MonitoringTestFixtures.callTrade();
        when(recommendations.at(any(), any(), any())).thenReturn(
                Optional.of(MonitoringTestFixtures.defaultCall()));
        when(recommendations.contextAt(any(), any(), any())).thenReturn(
                Optional.of(RecommendationTestFixtures.bullishContext()));
        when(queries.require(anyString())).thenReturn(
                new ActiveTradeMonitoring(fixture, Optional.empty()));

        TradePlan plan = plans.plan(MonitoringTestFixtures.defaultCall(),
                RecommendationTestFixtures.bullishContext()).orElseThrow();
        BigDecimal entry = new BigDecimal("106.00");

        service.register(new ManualTradeDtos.CreateTradeRequest("trade-1",
                "NSE_CASH:3045", "BUY_CALL", "ONE_MINUTE",
                RecommendationTestFixtures.START, "NIFTY-CE-500",
                new BigDecimal("500"), LocalDate.of(2026, 11, 26),
                RecommendationTestFixtures.START, entry, new BigDecimal("75"),
                "manual fill", null, null));

        ArgumentCaptor<ManualTrade> captured = ArgumentCaptor.forClass(ManualTrade.class);
        verify(monitoring).register(captured.capture());
        assertThat(captured.getValue().targetPremium().orElseThrow())
                .isEqualByComparingTo(entry.add(plan.premiumTargetDistance())
                        .setScale(2, RoundingMode.HALF_UP));
        assertThat(captured.getValue().stopPremium().orElseThrow())
                .isEqualByComparingTo(entry.subtract(plan.premiumStopDistance())
                        .setScale(2, RoundingMode.HALF_UP));
        assertThat(captured.getValue().targetPremium().orElseThrow())
                .isNotEqualByComparingTo(plan.targetPremium());
    }

    @Test
    void pinsTheExactRecommendationTheOwnerActedOn() {
        when(recommendations.at(MonitoringTestFixtures.SPOT,
                MonitoringTestFixtures.INTERVAL, RecommendationTestFixtures.START))
                .thenReturn(Optional.of(MonitoringTestFixtures.defaultCall()));
        when(recommendations.contextAt(any(), any(), any())).thenReturn(
                Optional.of(RecommendationTestFixtures.bullishContext()));
        when(queries.require(anyString())).thenReturn(new ActiveTradeMonitoring(
                MonitoringTestFixtures.callTrade(), Optional.empty()));

        service.register(request("BUY_CALL", "NIFTY-CE-500", null, null));

        verify(recommendations).at(MonitoringTestFixtures.SPOT,
                Interval.ONE_MINUTE, RecommendationTestFixtures.START);
        verify(recommendations, never()).latest(any(), any());
        verify(recommendations, never()).latestContext(any(), any());
    }

    @Test
    void rejectsAnExpiredPinnedRecommendationInsteadOfUsingANewerOne() {
        when(recommendations.at(any(), any(), any())).thenReturn(Optional.empty());
        when(recommendations.latest(any(), any())).thenReturn(
                Optional.of(MonitoringTestFixtures.defaultCall()));

        assertThatThrownBy(() -> service.register(
                request("BUY_CALL", "NIFTY-CE-500", null, null)))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("no longer current");
        verify(recommendations, never()).latest(any(), any());
        verify(monitoring, never()).register(any());
    }

    @Test
    void rejectsAFillWithoutAPinnedRecommendationTimestamp() {
        assertThatThrownBy(() -> service.register(
                new ManualTradeDtos.CreateTradeRequest("trade-1", "NSE_CASH:3045",
                        "BUY_CALL", "ONE_MINUTE", null, "NIFTY-CE-500",
                        new BigDecimal("500"), LocalDate.of(2026, 11, 26),
                        RecommendationTestFixtures.START, new BigDecimal("100"),
                        new BigDecimal("75"), "", null, null)))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("recommendationTimestamp is required");
        verify(monitoring, never()).register(any());
    }

    @Test
    void rejectsAFillWhoseDirectionDoesNotMatchThePinnedRecommendation() {
        when(recommendations.at(any(), any(), any())).thenReturn(
                Optional.of(MonitoringTestFixtures.defaultCall()));

        assertThatThrownBy(() -> service.register(
                request("BUY_PUT", "NIFTY-PE-500", null, null)))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("does not match the pinned recommendation");
    }

    @Test
    void closesTheLocalMonitoringRecord() {
        when(queries.require("trade-1")).thenReturn(
                new ActiveTradeMonitoring(MonitoringTestFixtures.callTrade().closed(),
                        Optional.empty()));

        service.close("trade-1", "owner exited");

        verify(monitoring).close("trade-1", "owner exited");
    }

    private static ManualTradeDtos.CreateTradeRequest request(
            String recommendationType,
            String optionSymbol,
            BigDecimal target,
            BigDecimal stop) {
        return new ManualTradeDtos.CreateTradeRequest("trade-1", "NSE_CASH:3045",
                recommendationType, "ONE_MINUTE", RecommendationTestFixtures.START,
                optionSymbol, new BigDecimal("500"), LocalDate.of(2026, 11, 26),
                RecommendationTestFixtures.START, new BigDecimal("100"),
                new BigDecimal("75"), "manual fill", target, stop);
    }
}
