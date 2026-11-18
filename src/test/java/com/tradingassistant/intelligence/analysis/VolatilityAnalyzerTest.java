package com.tradingassistant.intelligence.analysis;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.intelligence.config.MarketIntelligenceConfig;
import com.tradingassistant.intelligence.config.WarmupPolicy;
import com.tradingassistant.intelligence.domain.AnalysisContext;
import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.intelligence.domain.VolatilityAssessment;
import com.tradingassistant.intelligence.domain.VolatilityState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.tradingassistant.intelligence.IntelligenceTestFixtures.band;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.candle;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.context;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.result;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.timestamp;
import static org.assertj.core.api.Assertions.assertThat;

class VolatilityAnalyzerTest {

    private final VolatilityAnalyzer analyzer = new VolatilityAnalyzer();
    private final MarketIntelligenceConfig config = MarketIntelligenceConfig.defaults();

    @Test
    void reportsExpandingWhenEveryWidthGrows() {
        VolatilityAssessment assessment = analyzer.analyze(expanding(), config);

        assertThat(assessment.state()).isEqualTo(VolatilityState.EXPANDING);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.READY);
        assertThat(assessment.indicators())
                .containsExactlyInAnyOrder("ATR", "BOLLINGER", "KELTNER", "DONCHIAN");
    }

    @Test
    void reportsContractingWhenEveryWidthShrinks() {
        VolatilityAssessment assessment = analyzer.analyze(contracting(), config);

        assertThat(assessment.state()).isEqualTo(VolatilityState.CONTRACTING);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.READY);
    }

    @Test
    void reportsStableWhenMovementStaysInsideTheThreshold() {
        VolatilityAssessment assessment = analyzer.analyze(stable(), config);

        assertThat(assessment.state()).isEqualTo(VolatilityState.STABLE);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.READY);
    }

    @Test
    void sensitivityIsConfigurable() {
        MarketIntelligenceConfig insensitive = MarketIntelligenceConfig.builder()
                .volatilityChangeThreshold(new BigDecimal("0.5"))
                .build();

        VolatilityAssessment assessment = analyzer.analyze(expanding(), insensitive);

        assertThat(assessment.state()).isEqualTo(VolatilityState.STABLE);
    }

    @Test
    void reportsPartialWhenOnlyOneWidthIsComparable() {
        VolatilityAssessment assessment = analyzer.analyze(
                contextOf(List.of(atr("10", "12"))), config);

        assertThat(assessment.state()).isEqualTo(VolatilityState.EXPANDING);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.PARTIAL);
        assertThat(assessment.indicators()).containsExactly("ATR");
    }

    @Test
    void aSingleObservationCannotDescribeMovement() {
        VolatilityAssessment assessment = analyzer.analyze(
                contextOf(List.of(List.of(result("ATR", timestamp(1), "value", "12")))), config);

        assertThat(assessment.state()).isEqualTo(VolatilityState.UNKNOWN);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.INSUFFICIENT_DATA);
    }

    @Test
    void aZeroPreviousWidthIsNotComparable() {
        VolatilityAssessment assessment = analyzer.analyze(
                contextOf(List.of(atr("0", "10"))), config);

        assertThat(assessment.state()).isEqualTo(VolatilityState.UNKNOWN);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.INSUFFICIENT_DATA);
    }

    @Test
    void strictPolicyRefusesAPartialDescription() {
        MarketIntelligenceConfig strict = MarketIntelligenceConfig.builder()
                .warmupPolicy(WarmupPolicy.STRICT)
                .build();

        VolatilityAssessment assessment = analyzer.analyze(
                contextOf(List.of(atr("10", "12"))), strict);

        assertThat(assessment.state()).isEqualTo(VolatilityState.UNKNOWN);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.PARTIAL);
    }

    private static AnalysisContext expanding() {
        return contextOf(List.of(
                atr("10", "12"),
                bands("BOLLINGER", "105", "100", "106", "100"),
                bands("KELTNER", "104", "100", "104.8", "100"),
                bands("DONCHIAN", "106", "100", "107.2", "100")));
    }

    private static AnalysisContext contracting() {
        return contextOf(List.of(
                atr("12", "10"),
                bands("BOLLINGER", "106", "100", "105", "100"),
                bands("KELTNER", "104.8", "100", "104", "100"),
                bands("DONCHIAN", "107.2", "100", "106", "100")));
    }

    private static AnalysisContext stable() {
        return contextOf(List.of(
                atr("10", "10.1"),
                bands("BOLLINGER", "105", "100", "105.05", "100"),
                bands("KELTNER", "104", "100", "104.04", "100"),
                bands("DONCHIAN", "106", "100", "106.06", "100")));
    }

    private static List<IndicatorResult> atr(String previous, String current) {
        return List.of(
                result("ATR", timestamp(0), "value", previous),
                result("ATR", timestamp(1), "value", current));
    }

    private static List<IndicatorResult> bands(
            String type, String previousUpper, String previousLower, String upper, String lower) {
        return List.of(
                band(type, timestamp(0), previousUpper, previousLower),
                band(type, timestamp(1), upper, lower));
    }

    /**
     * Two-candle context built from per-indicator histories.
     */
    private static AnalysisContext contextOf(List<List<IndicatorResult>> histories) {
        Map<String, List<IndicatorResult>> byType = new LinkedHashMap<>();
        for (List<IndicatorResult> history : histories) {
            byType.put(history.getLast().indicator(), history);
        }
        return context(List.of(candle(0, "100"), candle(1, "101")), byType);
    }
}
