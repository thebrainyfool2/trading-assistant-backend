package com.tradingassistant.risk.config;

import com.tradingassistant.options.OptionsTestFixtures;
import com.tradingassistant.risk.RiskTestFixtures;
import com.tradingassistant.risk.exception.RiskValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static com.tradingassistant.risk.RiskTestFixtures.SPOT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiskEngineConfigTest {

    @Test
    void defaultsAreConservativeAndUsable() {
        RiskEngineConfig config = RiskEngineConfig.DEFAULT;

        assertThat(config.enabled()).isTrue();
        assertThat(config.referenceCapital()).isEqualByComparingTo("100000");
        assertThat(config.maximumRiskPercent()).isEqualByComparingTo("1");
        assertThat(config.maximumDailyLossPercent()).isEqualByComparingTo("3");
        assertThat(config.maximumSessionLossPercent()).isEqualByComparingTo("2");
        assertThat(config.maximumOpenExposurePercent()).isEqualByComparingTo("100");
        assertThat(config.defaultATRMultiplier()).isEqualByComparingTo("2");
        assertThat(config.minimumRewardRiskRatio()).isEqualByComparingTo("2");
        assertThat(config.minimumStopDistance()).isEqualByComparingTo("0.05");
        assertThat(config.maximumStopDistance()).isEqualByComparingTo("100");
        assertThat(config.maximumLeverage()).isEqualByComparingTo("5");
        assertThat(config.maximumQuantity()).isEqualByComparingTo("10000");
        assertThat(config.allowFractionalQuantity()).isFalse();
        assertThat(config.instrumentRiskPercentOverrides()).isEmpty();
        assertThat(config.parallelEvaluation()).isFalse();
        assertThat(config.failFast()).isFalse();
        assertThat(config.strictValidation()).isTrue();
    }

    @Test
    void builderOverridesEveryKnob() {
        RiskEngineConfig config = RiskEngineConfig.builder()
                .enabled(false)
                .referenceCapital(new BigDecimal("50000"))
                .maximumRiskPercent(new BigDecimal("0.5"))
                .maximumDailyLossPercent(new BigDecimal("2"))
                .maximumSessionLossPercent(new BigDecimal("1"))
                .maximumOpenExposurePercent(new BigDecimal("50"))
                .defaultATRMultiplier(new BigDecimal("3"))
                .minimumRewardRiskRatio(new BigDecimal("1.5"))
                .minimumStopDistance(new BigDecimal("0.1"))
                .maximumStopDistance(new BigDecimal("50"))
                .maximumLeverage(new BigDecimal("3"))
                .maximumQuantity(new BigDecimal("500"))
                .allowFractionalQuantity(true)
                .instrumentRiskPercentOverrides(Map.of(SPOT, new BigDecimal("0.25")))
                .parallelEvaluation(true)
                .failFast(true)
                .strictValidation(false)
                .build();

        assertThat(config.enabled()).isFalse();
        assertThat(config.referenceCapital()).isEqualByComparingTo("50000");
        assertThat(config.maximumRiskPercent()).isEqualByComparingTo("0.5");
        assertThat(config.maximumQuantity()).isEqualByComparingTo("500");
        assertThat(config.allowFractionalQuantity()).isTrue();
        assertThat(config.instrumentRiskPercentOverrides()).containsKey(SPOT);
        assertThat(config.parallelEvaluation()).isTrue();
        assertThat(config.failFast()).isTrue();
        assertThat(config.strictValidation()).isFalse();
    }

    @Test
    void toBuilderRoundTrips() {
        RiskEngineConfig config = RiskEngineConfig.builder()
                .referenceCapital(new BigDecimal("25000"))
                .allowFractionalQuantity(true)
                .build();

        assertThat(config.toBuilder().build()).isEqualTo(config);
    }

    @Test
    void rejectsAZeroOrNegativeCapital() {
        assertThatThrownBy(() -> RiskEngineConfig.builder()
                .referenceCapital(BigDecimal.ZERO)
                .build())
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("referenceCapital");
        assertThatThrownBy(() -> RiskEngineConfig.builder()
                .referenceCapital(new BigDecimal("-1"))
                .build())
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("referenceCapital");
    }

    @Test
    void rejectsAnUnusableRiskPercent() {
        assertThatThrownBy(() -> RiskEngineConfig.builder()
                .maximumRiskPercent(BigDecimal.ZERO)
                .build())
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("maximumRiskPercent");
        assertThatThrownBy(() -> RiskEngineConfig.builder()
                .maximumRiskPercent(new BigDecimal("101"))
                .build())
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("maximumRiskPercent");
    }

    @Test
    void rejectsASessionCapAboveTheDailyLimit() {
        assertThatThrownBy(() -> RiskEngineConfig.builder()
                .maximumDailyLossPercent(new BigDecimal("1"))
                .maximumSessionLossPercent(new BigDecimal("2"))
                .build())
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("maximumSessionLossPercent");
    }

    @Test
    void rejectsAnInvertedStopRange() {
        assertThatThrownBy(() -> RiskEngineConfig.builder()
                .minimumStopDistance(new BigDecimal("10"))
                .maximumStopDistance(new BigDecimal("10"))
                .build())
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("maximumStopDistance");
    }

    @Test
    void rejectsANegativeStopDistance() {
        assertThatThrownBy(() -> RiskEngineConfig.builder()
                .minimumStopDistance(new BigDecimal("-1"))
                .build())
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("minimumStopDistance");
    }

    @Test
    void rejectsAnUnusableLeverageCeiling() {
        assertThatThrownBy(() -> RiskEngineConfig.builder()
                .maximumLeverage(BigDecimal.ZERO)
                .build())
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("maximumLeverage");
        assertThatThrownBy(() -> RiskEngineConfig.builder()
                .maximumLeverage(new BigDecimal("101"))
                .build())
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("maximumLeverage");
    }

    @Test
    void rejectsAnUnusableRewardRiskRatio() {
        assertThatThrownBy(() -> RiskEngineConfig.builder()
                .minimumRewardRiskRatio(BigDecimal.ZERO)
                .build())
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("minimumRewardRiskRatio");
    }

    @Test
    void rejectsAnOverrideAboveTheGlobalRiskPercent() {
        assertThatThrownBy(() -> RiskEngineConfig.builder()
                .maximumRiskPercent(new BigDecimal("1"))
                .instrumentRiskPercentOverrides(Map.of(SPOT, new BigDecimal("2")))
                .build())
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("instrument risk percentage");
    }

    @Test
    void rejectsANullOrNonPositiveOverride() {
        RiskEngineConfig config = RiskEngineConfig.builder().build();
        assertThatThrownBy(() -> RiskEngineConfig.builder()
                .instrumentRiskPercentOverrides(Map.of(SPOT, BigDecimal.ZERO))
                .build())
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("instrument risk percentage");
        assertThat(config.hasOverride(SPOT)).isFalse();
    }

    @Test
    void derivesThePolicyLimitsFromCapital() {
        RiskEngineConfig config = RiskEngineConfig.DEFAULT;

        assertThat(config.dailyLossLimit()).isEqualByComparingTo("3000");
        assertThat(config.sessionLossLimit()).isEqualByComparingTo("2000");
        assertThat(config.maximumExposure()).isEqualByComparingTo("100000");
        assertThat(RiskEngineConfig.percentOf(new BigDecimal("200"), new BigDecimal("1.5")))
                .isEqualByComparingTo("3");
    }

    @Test
    void resolvesTheRiskPercentPerInstrument() {
        RiskEngineConfig config = RiskEngineConfig.builder()
                .instrumentRiskPercentOverrides(Map.of(SPOT, new BigDecimal("0.25")))
                .build();

        assertThat(config.riskPercentFor(SPOT)).isEqualByComparingTo("0.25");
        assertThat(config.hasOverride(SPOT)).isTrue();
        assertThat(config.riskPercentFor(OptionsTestFixtures.OTHER_SPOT))
                .isEqualByComparingTo("1");
        assertThat(config.hasOverride(OptionsTestFixtures.OTHER_SPOT)).isFalse();
        assertThat(config.riskPercentFor(null)).isEqualByComparingTo("1");
    }

    @Test
    void requireRejectsNull() {
        assertThatThrownBy(() -> RiskEngineConfig.require(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("config");
        assertThat(RiskTestFixtures.START).isNotNull();
    }
}
