package com.tradingassistant.intelligence.analysis;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.intelligence.config.MarketIntelligenceConfig;
import com.tradingassistant.intelligence.config.WarmupPolicy;
import com.tradingassistant.intelligence.domain.AnalysisContext;
import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.intelligence.domain.MomentumAssessment;
import com.tradingassistant.intelligence.domain.MomentumState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.tradingassistant.intelligence.IntelligenceTestFixtures.result;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.singleCandleContext;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.timestamp;
import static org.assertj.core.api.Assertions.assertThat;

class MomentumAnalyzerTest {

    private final MomentumAnalyzer analyzer = new MomentumAnalyzer();
    private final MarketIntelligenceConfig config = MarketIntelligenceConfig.defaults();

    @Test
    void reportsStrengtheningWhenInputsAgree() {
        MomentumAssessment assessment = analyzer.analyze(context(
                result("RSI", timestamp(0), "value", "70"),
                result("MACD", timestamp(0), "histogram", "0.5"),
                result("STOCH_RSI", timestamp(0), Map.of(
                        "percentK", new BigDecimal("60"),
                        "percentD", new BigDecimal("40")))), config);

        assertThat(assessment.state()).isEqualTo(MomentumState.STRENGTHENING);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.READY);
        assertThat(assessment.indicators()).containsExactlyInAnyOrder("RSI", "MACD", "STOCH_RSI");
    }

    @Test
    void reportsWeakeningWhenInputsAgree() {
        MomentumAssessment assessment = analyzer.analyze(context(
                result("RSI", timestamp(0), "value", "30"),
                result("MACD", timestamp(0), "histogram", "-0.2"),
                result("STOCH_RSI", timestamp(0), Map.of(
                        "percentK", new BigDecimal("20"),
                        "percentD", new BigDecimal("50")))), config);

        assertThat(assessment.state()).isEqualTo(MomentumState.WEAKENING);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.READY);
    }

    @Test
    void reportsNeutralWhenInputsAreBalanced() {
        MomentumAssessment assessment = analyzer.analyze(context(
                result("RSI", timestamp(0), "value", "50"),
                result("MACD", timestamp(0), "histogram", "0"),
                result("STOCH_RSI", timestamp(0), Map.of(
                        "percentK", new BigDecimal("45"),
                        "percentD", new BigDecimal("45")))), config);

        assertThat(assessment.state()).isEqualTo(MomentumState.NEUTRAL);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.READY);
    }

    @Test
    void thresholdsAreConfigurationRatherThanConstants() {
        MarketIntelligenceConfig tight = MarketIntelligenceConfig.builder()
                .momentumThresholds(new BigDecimal("45"), new BigDecimal("55"))
                .build();

        MomentumAssessment assessment = analyzer.analyze(context(
                result("RSI", timestamp(0), "value", "52")), tight);

        assertThat(assessment.state()).isEqualTo(MomentumState.NEUTRAL);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.PARTIAL);
    }

    @Test
    void reportsPartialWhenOnlyOneInputIsAvailable() {
        MomentumAssessment assessment = analyzer.analyze(context(
                result("RSI", timestamp(0), "value", "70")), config);

        assertThat(assessment.state()).isEqualTo(MomentumState.STRENGTHENING);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.PARTIAL);
        assertThat(assessment.indicators()).containsExactly("RSI");
    }

    @Test
    void reportsInsufficientWhenNothingIsUsable() {
        MomentumAssessment assessment = analyzer.analyze(context(
                result("MACD", timestamp(0), "histogram", "0")), MarketIntelligenceConfig.builder()
                .warmupPolicy(WarmupPolicy.STRICT)
                .build());

        assertThat(assessment.state()).isEqualTo(MomentumState.UNKNOWN);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.PARTIAL);
    }

    @Test
    void rejectsAnEmptyMomentumInputSetAsInsufficient() {
        MomentumAssessment assessment = analyzer.analyze(context(
                result("SMA", timestamp(0), "value", "100")), config);

        assertThat(assessment.state()).isEqualTo(MomentumState.UNKNOWN);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.INSUFFICIENT_DATA);
        assertThat(assessment.indicators()).isEmpty();
    }

    @Test
    void stochasticRsiNeedsBothLines() {
        MomentumAssessment assessment = analyzer.analyze(context(
                result("STOCH_RSI", timestamp(0), "percentK", "80")), config);

        assertThat(assessment.state()).isEqualTo(MomentumState.UNKNOWN);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.INSUFFICIENT_DATA);
        assertThat(assessment.indicators()).isEmpty();
    }

    private AnalysisContext context(IndicatorResult... results) {
        Map<String, IndicatorResult> latest = new LinkedHashMap<>();
        for (IndicatorResult result : results) {
            latest.put(result.indicator(), result);
        }
        return singleCandleContext("100", latest);
    }
}
