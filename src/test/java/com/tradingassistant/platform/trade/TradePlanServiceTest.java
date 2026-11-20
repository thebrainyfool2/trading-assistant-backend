package com.tradingassistant.platform.trade;

import com.tradingassistant.monitoring.MonitoringTestFixtures;
import com.tradingassistant.recommendation.RecommendationTestFixtures;
import com.tradingassistant.recommendation.domain.RecommendationType;
import com.tradingassistant.risk.config.RiskEngineConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TradePlanServiceTest {

    private final TradePlanService service = new TradePlanService(
            RiskEngineConfig.DEFAULT, TradePlanConfig.defaults(),
            Clock.fixed(RecommendationTestFixtures.START, ZoneOffset.UTC));

    @Test
    void derivesEntryTargetStopAndRiskSizedQuantityFromExistingEvidence() {
        TradePlan plan = service.plan(MonitoringTestFixtures.defaultCall(),
                RecommendationTestFixtures.bullishContext()).orElseThrow();

        assertThat(plan.direction()).isEqualTo(RecommendationType.BUY_CALL);
        assertThat(plan.suggestedEntry()).isPositive();
        assertThat(plan.targetPremium()).isGreaterThan(plan.suggestedEntry());
        assertThat(plan.stopPremium()).isLessThan(plan.suggestedEntry());
        assertThat(plan.delta()).isEqualByComparingTo("0.55");
        assertThat(plan.underlyingStopDistance()).isEqualByComparingTo("10");
        assertThat(plan.underlyingTargetDistance()).isEqualByComparingTo("20");
        assertThat(plan.premiumStopDistance()).isBetween(new BigDecimal("5.4"),
                new BigDecimal("5.6"));
        assertThat(plan.premiumTargetDistance()).isBetween(new BigDecimal("10.9"),
                new BigDecimal("11.1"));
        assertThat(plan.lotSize()).isEqualTo(75);
        assertThat(plan.suggestedQuantity()).isPositive();
        assertThat(plan.maximumLoss()).isPositive();
        assertThat(plan.potentialReward()).isPositive();
        assertThat(plan.rewardRiskRatio()).isEqualByComparingTo("2.00");
        assertThat(plan.basis()).contains("ATM_DELTA");
        assertThat(plan.diagnostics()).containsKeys("underlyingStopDistance",
                "deltaSource", "riskBudget");
    }

    @Test
    void noTradeHasNoPlan() {
        assertThat(service.plan(MonitoringTestFixtures.noTrade(),
                RecommendationTestFixtures.bullishContext())).isEmpty();
    }
}
