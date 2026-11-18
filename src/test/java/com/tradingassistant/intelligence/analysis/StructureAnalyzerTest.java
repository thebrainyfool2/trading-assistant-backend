package com.tradingassistant.intelligence.analysis;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.intelligence.config.MarketIntelligenceConfig;
import com.tradingassistant.intelligence.config.WarmupPolicy;
import com.tradingassistant.intelligence.domain.AnalysisContext;
import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.intelligence.domain.StructureAssessment;
import com.tradingassistant.intelligence.domain.StructureBias;
import com.tradingassistant.intelligence.domain.StructureSignal;
import com.tradingassistant.market.domain.Candle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.tradingassistant.intelligence.IntelligenceTestFixtures.candle;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.context;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.result;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.timestamp;
import static org.assertj.core.api.Assertions.assertThat;

class StructureAnalyzerTest {

    private static final MarketIntelligenceConfig CONFIG = MarketIntelligenceConfig.builder()
            .structureLookback(4)
            .historyWindow(20)
            .build();

    private final StructureAnalyzer analyzer = new StructureAnalyzer();

    @Test
    void recognisesHigherHighsAndLowsWithABreakoutCandidate() {
        StructureAssessment assessment = analyzer.analyze(contextOf(
                candle(0, "100", "101", "99", "100"),
                candle(1, "101", "102", "100", "101"),
                candle(2, "103", "105", "102", "104"),
                candle(3, "105", "108", "104", "107")), CONFIG);

        assertThat(assessment.bias()).isEqualTo(StructureBias.UPTREND);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.READY);
        assertThat(assessment.signals()).contains(
                StructureSignal.HIGHER_HIGH,
                StructureSignal.HIGHER_LOW,
                StructureSignal.BREAKOUT_CANDIDATE);
        assertThat(assessment.signals()).doesNotContain(StructureSignal.RANGE);
    }

    @Test
    void recognisesLowerHighsAndLowsWithABreakdownCandidate() {
        StructureAssessment assessment = analyzer.analyze(contextOf(
                candle(0, "108", "109", "107", "108"),
                candle(1, "107", "108", "106", "107"),
                candle(2, "105", "106", "103", "104"),
                candle(3, "103", "104", "100", "101")), CONFIG);

        assertThat(assessment.bias()).isEqualTo(StructureBias.DOWNTREND);
        assertThat(assessment.signals()).contains(
                StructureSignal.LOWER_HIGH,
                StructureSignal.LOWER_LOW,
                StructureSignal.BREAKDOWN_CANDIDATE);
    }

    @Test
    void recognisesARangeInsideTheTolerance() {
        StructureAssessment assessment = analyzer.analyze(contextOf(
                candle(0, "100", "100.2", "99.5", "99.8"),
                candle(1, "99.8", "100.1", "99.6", "100.0"),
                candle(2, "100.0", "100.3", "99.4", "99.9"),
                candle(3, "99.9", "100.2", "99.7", "100.1")), CONFIG);

        assertThat(assessment.bias()).isEqualTo(StructureBias.RANGING);
        assertThat(assessment.signals()).containsExactly(StructureSignal.RANGE);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.READY);
    }

    @Test
    void toleranceIsConfigurable() {
        MarketIntelligenceConfig exact = MarketIntelligenceConfig.builder()
                .structureLookback(4)
                .historyWindow(20)
                .structureRangeTolerancePercent(BigDecimal.ZERO)
                .build();

        StructureAssessment assessment = analyzer.analyze(contextOf(
                candle(0, "100", "100.2", "99.5", "99.8"),
                candle(1, "99.8", "100.1", "99.6", "100.0"),
                candle(2, "100.0", "100.3", "99.4", "99.9"),
                candle(3, "99.9", "100.2", "99.7", "100.1")), exact);

        assertThat(assessment.signals()).doesNotContain(StructureSignal.RANGE);
        assertThat(assessment.signals()).contains(
                StructureSignal.HIGHER_HIGH, StructureSignal.LOWER_LOW);
        assertThat(assessment.bias()).isEqualTo(StructureBias.UNKNOWN);
    }

    @Test
    void threeCandlesCannotDescribeStructure() {
        StructureAssessment assessment = analyzer.analyze(contextOf(
                candle(0, "100", "101", "99", "100"),
                candle(1, "101", "102", "100", "101"),
                candle(2, "103", "105", "102", "104")), CONFIG);

        assertThat(assessment.bias()).isEqualTo(StructureBias.UNKNOWN);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.INSUFFICIENT_DATA);
        assertThat(assessment.signals()).isEmpty();
    }

    @Test
    void aShortWindowIsReportedAsPartial() {
        MarketIntelligenceConfig longLookback = MarketIntelligenceConfig.builder()
                .structureLookback(20)
                .historyWindow(20)
                .build();
        Candle[] candles = new Candle[6];
        for (int index = 0; index < candles.length; index++) {
            BigDecimal close = BigDecimal.valueOf(100L + index);
            candles[index] = candle(index, close.toPlainString(),
                    close.add(BigDecimal.ONE).toPlainString(),
                    close.subtract(BigDecimal.ONE).toPlainString(),
                    close.toPlainString());
        }

        StructureAssessment assessment = analyzer.analyze(contextOf(candles), longLookback);

        assertThat(assessment.status()).isEqualTo(AssessmentStatus.PARTIAL);
        assertThat(assessment.bias()).isEqualTo(StructureBias.UPTREND);
    }

    @Test
    void strictPolicyRefusesAPartialStructure() {
        MarketIntelligenceConfig strict = MarketIntelligenceConfig.builder()
                .structureLookback(20)
                .historyWindow(20)
                .warmupPolicy(WarmupPolicy.STRICT)
                .build();

        StructureAssessment assessment = analyzer.analyze(contextOf(
                candle(0, "100", "101", "99", "100"),
                candle(1, "101", "102", "100", "101"),
                candle(2, "103", "105", "102", "104"),
                candle(3, "105", "108", "104", "107")), strict);

        assertThat(assessment.bias()).isEqualTo(StructureBias.UNKNOWN);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.PARTIAL);
        assertThat(assessment.signals()).contains(StructureSignal.HIGHER_HIGH);
    }

    private static AnalysisContext contextOf(Candle... candles) {
        List<Candle> series = List.of(candles);
        IndicatorResult sma = result("SMA", series.getLast().timestamp(), "value", "100");
        return context(series, Map.of("SMA", List.of(sma)));
    }
}
