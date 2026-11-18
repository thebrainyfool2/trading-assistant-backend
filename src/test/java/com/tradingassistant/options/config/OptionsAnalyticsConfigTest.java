package com.tradingassistant.options.config;

import com.tradingassistant.options.domain.ExpectedMoveMethod;
import com.tradingassistant.options.domain.PcrAggregation;
import com.tradingassistant.options.exception.OptionsValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptionsAnalyticsConfigTest {

    @Test
    void exposesDocumentedDefaults() {
        OptionsAnalyticsConfig config = OptionsAnalyticsConfig.DEFAULT;

        assertThat(config.atmDistancePercent()).isEqualByComparingTo(new BigDecimal("0.5"));
        assertThat(config.minimumOpenInterest()).isEqualTo(100L);
        assertThat(config.minimumVolume()).isEqualTo(100L);
        assertThat(config.maximumSpreadPercent()).isEqualByComparingTo(new BigDecimal("2.0"));
        assertThat(config.riskFreeRate()).isEqualByComparingTo(new BigDecimal("0.065"));
        assertThat(config.dayCount()).isEqualTo(365);
        assertThat(config.pcrAggregation()).isEqualTo(PcrAggregation.BOTH);
        assertThat(config.expectedMoveMethod()).isEqualTo(ExpectedMoveMethod.VOLATILITY);
        assertThat(config.exchangeZone()).isEqualTo(ZoneId.of("Asia/Kolkata"));
    }

    @Test
    void appliesBuilderOverrides() {
        OptionsAnalyticsConfig config = OptionsAnalyticsConfig.builder()
                .atmDistancePercent(new BigDecimal("1.5"))
                .minimumOpenInterest(500L)
                .expectedMoveMethod(ExpectedMoveMethod.STRADDLE)
                .pcrAggregation(PcrAggregation.EXPIRY)
                .build();

        assertThat(config.atmDistancePercent()).isEqualByComparingTo(new BigDecimal("1.5"));
        assertThat(config.minimumOpenInterest()).isEqualTo(500L);
        assertThat(config.expectedMoveMethod()).isEqualTo(ExpectedMoveMethod.STRADDLE);
        assertThat(config.pcrAggregation()).isEqualTo(PcrAggregation.EXPIRY);
    }

    @Test
    void rebuildsFromAnExistingConfiguration() {
        OptionsAnalyticsConfig modified = OptionsAnalyticsConfig.DEFAULT.toBuilder()
                .ivHistoryWindow(12)
                .build();

        assertThat(modified.ivHistoryWindow()).isEqualTo(12);
        assertThat(modified.atmDistancePercent())
                .isEqualByComparingTo(OptionsAnalyticsConfig.DEFAULT.atmDistancePercent());
        assertThat(modified.riskFreeRate())
                .isEqualByComparingTo(OptionsAnalyticsConfig.DEFAULT.riskFreeRate());
    }

    @Test
    void computesYearFractions() {
        OptionsAnalyticsConfig config = OptionsAnalyticsConfig.builder().dayCount(365).build();

        assertThat(config.yearFraction(73).doubleValue()).isCloseTo(0.2,
                org.assertj.core.api.Assertions.within(1.0e-12));
        assertThat(config.dailyYearFraction().doubleValue()).isCloseTo(1.0 / 365.0,
                org.assertj.core.api.Assertions.within(1.0e-12));
    }

    @Test
    void rejectsImpossiblePercentages() {
        assertThatThrownBy(() -> OptionsAnalyticsConfig.builder()
                .atmDistancePercent(new BigDecimal("-1")).build())
                .isInstanceOf(OptionsValidationException.class)
                .hasMessageContaining("atmDistancePercent");
        assertThatThrownBy(() -> OptionsAnalyticsConfig.builder()
                .maximumSpreadPercent(BigDecimal.ZERO).build())
                .isInstanceOf(OptionsValidationException.class)
                .hasMessageContaining("maximumSpreadPercent must be positive");
    }

    @Test
    void rejectsInvalidVolatilityBounds() {
        assertThatThrownBy(() -> OptionsAnalyticsConfig.builder()
                .minimumVolatility(BigDecimal.ZERO).build())
                .isInstanceOf(OptionsValidationException.class)
                .hasMessageContaining("minimumVolatility must be positive");
        assertThatThrownBy(() -> OptionsAnalyticsConfig.builder()
                .minimumVolatility(new BigDecimal("2"))
                .maximumVolatility(new BigDecimal("1"))
                .build())
                .isInstanceOf(OptionsValidationException.class)
                .hasMessageContaining("maximumVolatility must be greater");
    }

    @Test
    void rejectsInvalidWindowsAndCounts() {
        assertThatThrownBy(() -> OptionsAnalyticsConfig.builder().ivHistoryWindow(1).build())
                .isInstanceOf(OptionsValidationException.class)
                .hasMessageContaining("ivHistoryWindow");
        assertThatThrownBy(() -> OptionsAnalyticsConfig.builder().dayCount(0).build())
                .isInstanceOf(OptionsValidationException.class)
                .hasMessageContaining("dayCount");
        assertThatThrownBy(() -> OptionsAnalyticsConfig.builder().smileStrikeRange(0).build())
                .isInstanceOf(OptionsValidationException.class)
                .hasMessageContaining("smileStrikeRange");
        assertThatThrownBy(() -> OptionsAnalyticsConfig.builder()
                .maxPainNearestStrikeCount(0).build())
                .isInstanceOf(OptionsValidationException.class)
                .hasMessageContaining("maxPainNearestStrikeCount");
    }

    @Test
    void rejectsMissingEnumerations() {
        assertThatThrownBy(() -> OptionsAnalyticsConfig.builder().pcrAggregation(null).build())
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> OptionsAnalyticsConfig.builder().expectedMoveMethod(null).build())
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> OptionsAnalyticsConfig.builder().exchangeZone(null).build())
                .isInstanceOf(NullPointerException.class);
    }
}
