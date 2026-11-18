package com.tradingassistant.risk.math;

import com.tradingassistant.intelligence.domain.VolatilityState;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.risk.RiskTestFixtures;
import com.tradingassistant.risk.config.RiskEngineConfig;
import com.tradingassistant.risk.domain.RiskContext;
import com.tradingassistant.risk.domain.RiskPlan;
import com.tradingassistant.risk.domain.StopMethodology;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static com.tradingassistant.risk.RiskTestFixtures.ATR;
import static com.tradingassistant.risk.RiskTestFixtures.PRICE;
import static com.tradingassistant.risk.RiskTestFixtures.SPOT;
import static com.tradingassistant.risk.RiskTestFixtures.START;
import static com.tradingassistant.risk.RiskTestFixtures.indicators;
import static com.tradingassistant.risk.RiskTestFixtures.atrIndicator;
import static org.assertj.core.api.Assertions.assertThat;

class RiskCalculatorTest {

    private final RiskCalculator calculator = new RiskCalculator();
    private final RiskEngineConfig config = RiskEngineConfig.DEFAULT;

    @Test
    void derivesStopAndSizeFromAtrAndRiskBudget() {
        RiskPlan plan = calculator.calculate(RiskTestFixtures.context(), config);

        assertThat(plan.capital()).isEqualByComparingTo("100000");
        assertThat(plan.riskPercent()).isEqualByComparingTo("1");
        assertThat(plan.riskAmount()).isEqualByComparingTo("1000");
        assertThat(plan.stop().methodology()).isEqualTo(StopMethodology.ATR);
        assertThat(plan.stopDistance()).isEqualByComparingTo("10");
        assertThat(plan.stop().distancePercent()).isEqualByComparingTo("2");
        assertThat(plan.quantity()).isEqualByComparingTo("100");
        assertThat(plan.positionValue()).isEqualByComparingTo("50000");
        assertThat(plan.requiredLeverage()).isEqualByComparingTo("0.5");
        assertThat(plan.sizingAvailable()).isTrue();
        assertThat(plan.stopAvailable()).isTrue();
    }

    @Test
    void appliesExpandingVolatilityAdjustment() {
        RiskPlan plan = calculator.calculate(
                RiskTestFixtures.context(VolatilityState.EXPANDING), config);

        assertThat(plan.volatilityAdjustment()).isEqualByComparingTo("1.25");
        assertThat(plan.atrMultiple()).isEqualByComparingTo("2.5");
        assertThat(plan.stopDistance()).isEqualByComparingTo("12.5");
        assertThat(plan.quantity()).isEqualByComparingTo("80");
    }

    @Test
    void appliesContractingVolatilityAdjustment() {
        RiskPlan plan = calculator.calculate(
                RiskTestFixtures.context(VolatilityState.CONTRACTING), config);

        assertThat(plan.volatilityAdjustment()).isEqualByComparingTo("0.85");
        assertThat(plan.atrMultiple()).isEqualByComparingTo("1.7");
        assertThat(plan.stopDistance()).isEqualByComparingTo("8.5");
        assertThat(plan.quantity()).isEqualByComparingTo("117");
    }

    @Test
    void keepsANeutralAdjustmentForStableAndUnknownVolatility() {
        assertThat(RiskCalculator.volatilityAdjustment(VolatilityState.STABLE))
                .isEqualByComparingTo("1");
        assertThat(RiskCalculator.volatilityAdjustment(VolatilityState.UNKNOWN))
                .isEqualByComparingTo("1");
        assertThat(RiskCalculator.volatilityAdjustment(null)).isEqualByComparingTo("1");
        assertThat(RiskCalculator.volatilityAdjustment(VolatilityState.EXPANDING))
                .isEqualByComparingTo("1.25");
        assertThat(RiskCalculator.volatilityAdjustment(VolatilityState.CONTRACTING))
                .isEqualByComparingTo("0.85");
    }

    @Test
    void keepsAFractionalQuantityWhenPermitted() {
        RiskEngineConfig fractional = RiskEngineConfig.builder()
                .allowFractionalQuantity(true)
                .build();

        RiskPlan plan = calculator.calculate(
                RiskTestFixtures.context(VolatilityState.CONTRACTING), fractional);

        assertThat(plan.quantity()).isEqualByComparingTo("117.6471");
        assertThat(plan.fractionalApplied()).isTrue();
    }

    @Test
    void returnsAPlanWithoutStopWhenAtrIsMissing() {
        RiskPlan plan = calculator.calculate(RiskTestFixtures.contextWithoutAtr(), config);

        assertThat(plan.atr()).isEmpty();
        assertThat(plan.stopAvailable()).isFalse();
        assertThat(plan.stop().methodology()).isEqualTo(StopMethodology.NONE);
        assertThat(plan.sizing()).isEmpty();
        assertThat(plan.riskAmount()).isEqualByComparingTo("1000");
    }

    @Test
    void returnsAPlanWithoutSizingWhenTheQuantityRoundsToZero() {
        RiskEngineConfig tiny = RiskEngineConfig.builder()
                .referenceCapital(new BigDecimal("100"))
                .maximumRiskPercent(new BigDecimal("0.01"))
                .build();

        RiskPlan plan = calculator.calculate(RiskTestFixtures.context(), tiny);

        assertThat(plan.riskAmount()).isEqualByComparingTo("0.01");
        assertThat(plan.stopAvailable()).isTrue();
        assertThat(plan.sizing()).isEmpty();
    }

    @Test
    void appliesAnInstrumentRiskPercentOverride() {
        RiskEngineConfig overridden = RiskEngineConfig.builder()
                .instrumentRiskPercentOverrides(Map.of(SPOT, new BigDecimal("0.5")))
                .build();

        RiskPlan plan = calculator.calculate(RiskTestFixtures.context(), overridden);

        assertThat(plan.riskPercent()).isEqualByComparingTo("0.5");
        assertThat(plan.riskAmount()).isEqualByComparingTo("500");
        assertThat(plan.quantity()).isEqualByComparingTo("50");
        assertThat(overridden.hasOverride(SPOT)).isTrue();
    }

    @Test
    void usesTheOptionSpotWhenNoCandleExists() {
        RiskContext context = new RiskContext(RiskTestFixtures.strategyEvaluation(),
                RiskTestFixtures.qualification(), RiskTestFixtures.marketAssessment(),
                Optional.of(RiskTestFixtures.optionsAssessment()), Optional.empty(),
                indicators(atrIndicator(ATR)));

        RiskPlan plan = calculator.calculate(context, config);

        assertThat(plan.sizing()).isPresent();
        assertThat(plan.sizing().orElseThrow().referencePrice())
                .isEqualByComparingTo(PRICE);
        assertThat(plan.quantity()).isEqualByComparingTo("100");
    }

    @Test
    void readsTheAvailableRewardFromTheExpectedMove() {
        RiskPlan plan = calculator.calculate(RiskTestFixtures.context(), config);

        assertThat(plan.availableReward()).isPresent();
        assertThat(plan.availableReward().orElseThrow()).isEqualByComparingTo("25");
    }

    @Test
    void reportsNoRewardWithoutAnOptionAssessment() {
        RiskPlan plan = calculator.calculate(RiskTestFixtures.contextWithoutOptions(), config);

        assertThat(plan.availableReward()).isEmpty();
    }

    @Test
    void usesTheContextIndicatorKeyCanonicalisation() {
        RiskContext context = new RiskContext(RiskTestFixtures.strategyEvaluation(),
                RiskTestFixtures.qualification(), RiskTestFixtures.marketAssessment(),
                Optional.of(RiskTestFixtures.optionsAssessment()),
                Optional.of(RiskTestFixtures.candle()),
                Map.of("atr", atrIndicator(ATR)));

        assertThat(context.atr()).isPresent();
        assertThat(calculator.calculate(context, config).stopAvailable()).isTrue();
    }

    @Test
    void ignoresAnUnrelatedInterval() {
        RiskContext context = new RiskContext(RiskTestFixtures.strategyEvaluation(),
                RiskTestFixtures.qualification(), RiskTestFixtures.marketAssessment(),
                Optional.of(RiskTestFixtures.optionsAssessment()),
                Optional.of(RiskTestFixtures.candle()),
                indicators(atrIndicator(ATR)));

        assertThat(context.interval()).isEqualTo(Interval.ONE_MINUTE);
        assertThat(context.instrument()).isEqualTo(SPOT);
        assertThat(context.timestamp()).isEqualTo(START);
        assertThat(context.price()).isPresent();
        assertThat(context.price().orElseThrow()).isEqualByComparingTo(PRICE);
    }
}
