package com.tradingassistant.intelligence.analysis;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.intelligence.config.MarketIntelligenceConfig;
import com.tradingassistant.intelligence.config.WarmupPolicy;
import com.tradingassistant.intelligence.domain.AnalysisContext;
import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.intelligence.domain.TrendAssessment;
import com.tradingassistant.intelligence.domain.TrendState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.tradingassistant.intelligence.IntelligenceTestFixtures.result;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.singleCandleContext;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.timestamp;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.warming;
import static org.assertj.core.api.Assertions.assertThat;

class TrendAnalyzerTest {

    private final TrendAnalyzer analyzer = new TrendAnalyzer();
    private final MarketIntelligenceConfig config = MarketIntelligenceConfig.defaults();

    @Test
    void reportsBullishWhenInputsAgreeAndAdxConfirms() {
        TrendAssessment assessment = analyzer.analyze(context("110",
                indicator("EMA", "100"), indicator("SMA", "105"),
                indicator("SUPERTREND", "direction", "1"), indicator("ADX", "adx", "30")), config);

        assertThat(assessment.state()).isEqualTo(TrendState.BULLISH);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.READY);
        assertThat(assessment.indicators()).containsExactlyInAnyOrder("EMA", "SMA", "SUPERTREND", "ADX");
        assertThat(assessment.summary()).isNotBlank();
    }

    @Test
    void reportsBearishWhenInputsAgreeDownward() {
        TrendAssessment assessment = analyzer.analyze(context("90",
                indicator("EMA", "100"), indicator("SMA", "95"),
                indicator("SUPERTREND", "direction", "-1"), indicator("ADX", "adx", "40")), config);

        assertThat(assessment.state()).isEqualTo(TrendState.BEARISH);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.READY);
    }

    @Test
    void reportsSidewaysWhenInputsConflict() {
        TrendAssessment assessment = analyzer.analyze(context("100",
                indicator("EMA", "100"), indicator("SMA", "105"),
                indicator("SUPERTREND", "direction", "1"), indicator("ADX", "adx", "30")), config);

        assertThat(assessment.state()).isEqualTo(TrendState.SIDEWAYS);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.READY);
    }

    @Test
    void reportsSidewaysWhenAdxDoesNotConfirmStrength() {
        TrendAssessment assessment = analyzer.analyze(context("110",
                indicator("EMA", "100"), indicator("SMA", "105"),
                indicator("SUPERTREND", "direction", "1"), indicator("ADX", "adx", "10")), config);

        assertThat(assessment.state()).isEqualTo(TrendState.SIDEWAYS);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.READY);
    }

    @Test
    void reportsUnknownWhenRequiredAdxIsMissing() {
        TrendAssessment assessment = analyzer.analyze(context("110",
                indicator("EMA", "100"), indicator("SMA", "105"),
                indicator("SUPERTREND", "direction", "1")), config);

        assertThat(assessment.state()).isEqualTo(TrendState.UNKNOWN);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.PARTIAL);
        assertThat(assessment.indicators()).doesNotContain("ADX");
    }

    @Test
    void oneAgreeingInputIsNotEnoughByDefault() {
        TrendAssessment assessment = analyzer.analyze(context("110",
                indicator("EMA", "100"), indicator("ADX", "adx", "30")), config);

        assertThat(assessment.state()).isEqualTo(TrendState.SIDEWAYS);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.PARTIAL);
        assertThat(assessment.indicators()).containsExactlyInAnyOrder("EMA", "ADX");
    }

    @Test
    void adxAloneDoesNotDescribeADirection() {
        TrendAssessment assessment = analyzer.analyze(
                context("110", indicator("ADX", "adx", "30")), config);

        assertThat(assessment.state()).isEqualTo(TrendState.UNKNOWN);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.PARTIAL);
        assertThat(assessment.indicators()).containsExactly("ADX");
    }

    @Test
    void warmingUpIndicatorsAreNotAssumed() {
        TrendAssessment assessment = analyzer.analyze(
                context("110", Map.of("EMA", warming("EMA", timestamp(0)))), config);

        assertThat(assessment.state()).isEqualTo(TrendState.UNKNOWN);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.INSUFFICIENT_DATA);
        assertThat(assessment.indicators()).isEmpty();
    }

    @Test
    void strictPolicyRefusesAPartialDescription() {
        MarketIntelligenceConfig strict = MarketIntelligenceConfig.builder()
                .warmupPolicy(WarmupPolicy.STRICT)
                .build();

        TrendAssessment assessment = analyzer.analyze(
                context("110", indicator("EMA", "100")), strict);

        assertThat(assessment.state()).isEqualTo(TrendState.UNKNOWN);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.PARTIAL);
    }

    @Test
    void adxCanBeOptionalByConfiguration() {
        MarketIntelligenceConfig relaxed = MarketIntelligenceConfig.builder()
                .requireAdxForDirection(false)
                .build();

        TrendAssessment assessment = analyzer.analyze(context("110",
                indicator("EMA", "100"), indicator("SMA", "105")), relaxed);

        assertThat(assessment.state()).isEqualTo(TrendState.BULLISH);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.PARTIAL);
        assertThat(assessment.indicators()).doesNotContain("ADX");
    }

    @Test
    void agreementRequirementIsConfigurable() {
        MarketIntelligenceConfig unanimous = MarketIntelligenceConfig.builder()
                .trendAgreementVotes(3)
                .build();

        TrendAssessment assessment = analyzer.analyze(context("110",
                indicator("EMA", "100"), indicator("SMA", "105"),
                indicator("ADX", "adx", "30")), unanimous);

        assertThat(assessment.state()).isEqualTo(TrendState.SIDEWAYS);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.PARTIAL);
    }

    private AnalysisContext context(String close, IndicatorResult... results) {
        Map<String, IndicatorResult> latest = new java.util.LinkedHashMap<>();
        for (IndicatorResult result : results) {
            latest.put(result.indicator(), result);
        }
        return singleCandleContext(close, latest);
    }

    private AnalysisContext context(String close, Map<String, IndicatorResult> latest) {
        return singleCandleContext(close, latest);
    }

    private static IndicatorResult indicator(String type, String value) {
        return result(type, timestamp(0), "value", value);
    }

    private static IndicatorResult indicator(String type, String key, String value) {
        return result(type, timestamp(0), key, value);
    }
}
