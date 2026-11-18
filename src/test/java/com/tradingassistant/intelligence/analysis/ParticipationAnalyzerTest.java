package com.tradingassistant.intelligence.analysis;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.intelligence.config.MarketIntelligenceConfig;
import com.tradingassistant.intelligence.domain.AnalysisContext;
import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.intelligence.domain.ParticipationAssessment;
import com.tradingassistant.intelligence.domain.ParticipationState;
import com.tradingassistant.market.domain.Candle;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.tradingassistant.intelligence.IntelligenceTestFixtures.candle;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.context;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.result;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.timestamp;
import static org.assertj.core.api.Assertions.assertThat;

class ParticipationAnalyzerTest {

    private final ParticipationAnalyzer analyzer = new ParticipationAnalyzer();
    private final MarketIntelligenceConfig config = MarketIntelligenceConfig.defaults();

    @Test
    void reportsStrongWhenVolumeInputsAgree() {
        ParticipationAssessment assessment = analyzer.analyze(
                contextOf("110", obv("100", "150"),
                        latest(result("CMF", timestamp(1), "value", "0.2")),
                        latest(result("VWAP", timestamp(1), "value", "90"))),
                config);

        assertThat(assessment.state()).isEqualTo(ParticipationState.STRONG);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.READY);
        assertThat(assessment.indicators()).containsExactlyInAnyOrder("OBV", "CMF", "VWAP");
    }

    @Test
    void reportsWeakWhenVolumeInputsAgree() {
        ParticipationAssessment assessment = analyzer.analyze(
                contextOf("100", obv("150", "100"),
                        latest(result("CMF", timestamp(1), "value", "-0.2")),
                        latest(result("VWAP", timestamp(1), "value", "120"))),
                config);

        assertThat(assessment.state()).isEqualTo(ParticipationState.WEAK);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.READY);
    }

    @Test
    void reportsAverageWhenInputsBalance() {
        ParticipationAssessment assessment = analyzer.analyze(
                contextOf("100", obv("100", "100"),
                        latest(result("CMF", timestamp(1), "value", "0.0")),
                        latest(result("VWAP", timestamp(1), "value", "100"))),
                config);

        assertThat(assessment.state()).isEqualTo(ParticipationState.AVERAGE);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.READY);
    }

    @Test
    void reportsPartialWhenOnlyOneInputIsUsable() {
        ParticipationAssessment assessment = analyzer.analyze(
                contextOf("100", latest(result("CMF", timestamp(1), "value", "0.2"))), config);

        assertThat(assessment.state()).isEqualTo(ParticipationState.STRONG);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.PARTIAL);
        assertThat(assessment.indicators()).containsExactly("CMF");
    }

    @Test
    void obvNeedsAPreviousObservation() {
        ParticipationAssessment assessment = analyzer.analyze(
                contextOf("100", latest(result("OBV", timestamp(1), "value", "150"))), config);

        assertThat(assessment.state()).isEqualTo(ParticipationState.UNKNOWN);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.INSUFFICIENT_DATA);
        assertThat(assessment.indicators()).isEmpty();
    }

    @Test
    void cmfThresholdIsConfigurable() {
        MarketIntelligenceConfig strictCmf = MarketIntelligenceConfig.builder()
                .participationCmfThreshold(new java.math.BigDecimal("0.5"))
                .build();

        ParticipationAssessment assessment = analyzer.analyze(
                contextOf("100", latest(result("CMF", timestamp(1), "value", "0.2"))), strictCmf);

        assertThat(assessment.state()).isEqualTo(ParticipationState.AVERAGE);
    }

    @SafeVarargs
    private static AnalysisContext contextOf(
            String close,
            List<IndicatorResult>... histories) {
        Map<String, List<IndicatorResult>> byType = new LinkedHashMap<>();
        for (List<IndicatorResult> history : histories) {
            byType.put(history.getLast().indicator(), history);
        }
        List<Candle> candles = List.of(candle(0, "100"), candle(1, close));
        return context(candles, byType);
    }

    private static List<IndicatorResult> latest(IndicatorResult result) {
        return List.of(result);
    }

    private static List<IndicatorResult> obv(String previous, String current) {
        return List.of(
                result("OBV", timestamp(0), "value", previous),
                result("OBV", timestamp(1), "value", current));
    }
}
