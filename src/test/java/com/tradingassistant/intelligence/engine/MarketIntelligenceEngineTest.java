package com.tradingassistant.intelligence.engine;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.intelligence.IntelligenceTestFixtures;
import com.tradingassistant.intelligence.api.MarketAnalyzer;
import com.tradingassistant.intelligence.config.MarketIntelligenceConfig;
import com.tradingassistant.intelligence.domain.AnalysisContext;
import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.intelligence.domain.ConfidenceLevel;
import com.tradingassistant.intelligence.domain.MarketAssessment;
import com.tradingassistant.intelligence.domain.MomentumAssessment;
import com.tradingassistant.intelligence.domain.MomentumState;
import com.tradingassistant.intelligence.domain.ParticipationState;
import com.tradingassistant.intelligence.domain.SessionPhase;
import com.tradingassistant.intelligence.domain.StructureBias;
import com.tradingassistant.intelligence.domain.TrendState;
import com.tradingassistant.intelligence.domain.VolatilityState;
import com.tradingassistant.intelligence.exception.IntelligenceValidationException;
import com.tradingassistant.market.domain.Candle;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.tradingassistant.intelligence.IntelligenceTestFixtures.band;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.candle;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.contextWithLatest;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.result;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.risingCandles;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.timestamp;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.warming;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketIntelligenceEngineTest {

    private final MarketIntelligenceConfig config = MarketIntelligenceConfig.defaults();
    private final MarketIntelligenceEngine engine = MarketIntelligenceEngine.standard(config);

    @Test
    void composesEveryAspectOfAFullyReadyMarket() {
        MarketAssessment assessment = engine.assess(fullContext());

        assertThat(assessment.status()).isEqualTo(AssessmentStatus.READY);
        assertThat(assessment.warmup()).isFalse();
        assertThat(assessment.confidence().level()).isEqualTo(ConfidenceLevel.HIGH);
        assertThat(assessment.confidence().basis()).isEqualTo("5 of 5 market aspects ready");
        assertThat(assessment.instrument()).isEqualTo(IntelligenceTestFixtures.KEY);
        assertThat(assessment.timestamp()).isEqualTo(timestamp(19));
        assertThat(assessment.trend().state()).isEqualTo(TrendState.BULLISH);
        assertThat(assessment.momentum().state()).isEqualTo(MomentumState.STRENGTHENING);
        assertThat(assessment.volatility().state()).isEqualTo(VolatilityState.EXPANDING);
        assertThat(assessment.participation().state()).isEqualTo(ParticipationState.STRONG);
        assertThat(assessment.structure().bias()).isEqualTo(StructureBias.UPTREND);
        assertThat(assessment.session().phase()).isEqualTo(SessionPhase.REGULAR);
        assertThat(assessment.ranging()).isFalse();
    }

    @Test
    void describesWithoutEveryInputAndSaysSo() {
        List<Candle> candles = risingCandles(20);
        Candle latest = candles.getLast();
        AnalysisContext context = contextWithLatest(candles, Map.of(
                "SMA", result("SMA", latest.timestamp(), "value", "118")));

        MarketAssessment assessment = engine.assess(context);

        assertThat(assessment.status()).isEqualTo(AssessmentStatus.PARTIAL);
        assertThat(assessment.warmup()).isTrue();
        assertThat(assessment.confidence().level()).isEqualTo(ConfidenceLevel.LOW);
        assertThat(assessment.trend().state()).isEqualTo(TrendState.UNKNOWN);
        assertThat(assessment.momentum().status()).isEqualTo(AssessmentStatus.INSUFFICIENT_DATA);
        assertThat(assessment.structure().bias()).isEqualTo(StructureBias.UPTREND);
    }

    @Test
    void reportsInsufficientDataWhenNoPriceAspectCanBeDescribed() {
        List<Candle> candles = List.of(candle(0, "100"), candle(1, "101"), candle(2, "102"));
        Candle latest = candles.getLast();
        AnalysisContext context = contextWithLatest(candles, Map.of(
                "SMA", warming("SMA", latest.timestamp())));

        MarketAssessment assessment = engine.assess(context);

        assertThat(assessment.status()).isEqualTo(AssessmentStatus.INSUFFICIENT_DATA);
        assertThat(assessment.warmup()).isTrue();
        assertThat(assessment.confidence().level()).isEqualTo(ConfidenceLevel.UNKNOWN);
        assertThat(assessment.session().status()).isEqualTo(AssessmentStatus.READY);
    }

    @Test
    void rejectsAContextWithoutARequiredIndicator() {
        MarketIntelligenceConfig required = MarketIntelligenceConfig.builder()
                .requiredIndicators(Set.of("ADX"))
                .build();
        MarketIntelligenceEngine strictEngine = MarketIntelligenceEngine.standard(required);
        List<Candle> candles = risingCandles(20);
        Candle latest = candles.getLast();
        AnalysisContext context = contextWithLatest(candles, Map.of(
                "SMA", result("SMA", latest.timestamp(), "value", "118")));

        assertThatThrownBy(() -> strictEngine.assess(context))
                .isInstanceOf(IntelligenceValidationException.class)
                .hasMessageContaining("ADX");
    }

    @Test
    void rejectsANullContext() {
        assertThatThrownBy(() -> engine.assess(null))
                .isInstanceOf(IntelligenceValidationException.class);
    }

    @Test
    void assessmentIsDeterministicAndImmutable() {
        MarketAssessment first = engine.assess(fullContext());
        MarketAssessment second = engine.assess(fullContext());

        assertThat(first).isEqualTo(second);
        assertThatThrownBy(() -> first.trend().indicators().add("ADX"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void compositionAcceptsReplacementAnalyzers() {
        MarketAnalyzer<MomentumAssessment> fixedMomentum = new MarketAnalyzer<>() {
            @Override
            public String name() {
                return "fixed-momentum";
            }

            @Override
            public MomentumAssessment analyze(
                    AnalysisContext context, MarketIntelligenceConfig ignored) {
                return new MomentumAssessment(
                        MomentumState.WEAKENING, AssessmentStatus.READY, Set.of("TEST"), "Replaced.");
            }
        };
        MarketIntelligenceEngine replaced = new MarketIntelligenceEngine(
                config,
                new com.tradingassistant.intelligence.analysis.TrendAnalyzer(),
                fixedMomentum,
                new com.tradingassistant.intelligence.analysis.VolatilityAnalyzer(),
                new com.tradingassistant.intelligence.analysis.ParticipationAnalyzer(),
                new com.tradingassistant.intelligence.analysis.StructureAnalyzer(),
                new com.tradingassistant.intelligence.analysis.SessionContextAnalyzer());

        MarketAssessment assessment = replaced.assess(fullContext());

        assertThat(assessment.momentum().state()).isEqualTo(MomentumState.WEAKENING);
        assertThat(replaced.analyzerNames()).contains("fixed-momentum");
    }

    private AnalysisContext fullContext() {
        List<Candle> candles = risingCandles(20);
        Candle latest = candles.getLast();
        Candle previous = candles.get(candles.size() - 2);
        var latestAt = latest.timestamp();
        var previousAt = previous.timestamp();

        Map<String, List<IndicatorResult>> histories = new LinkedHashMap<>();
        histories.put("EMA", List.of(
                result("EMA", previousAt, "value", "117"),
                result("EMA", latestAt, "value", "118")));
        histories.put("SMA", List.of(
                result("SMA", previousAt, "value", "117.5"),
                result("SMA", latestAt, "value", "118.5")));
        histories.put("SUPERTREND", List.of(result("SUPERTREND", latestAt, "direction", "1")));
        histories.put("ADX", List.of(result("ADX", latestAt, "adx", "30")));
        histories.put("RSI", List.of(result("RSI", latestAt, "value", "70")));
        histories.put("MACD", List.of(result("MACD", latestAt, "histogram", "0.5")));
        histories.put("STOCH_RSI", List.of(result("STOCH_RSI", latestAt, Map.of(
                "percentK", new java.math.BigDecimal("60"),
                "percentD", new java.math.BigDecimal("40")))));
        histories.put("ATR", List.of(
                result("ATR", previousAt, "value", "10"),
                result("ATR", latestAt, "value", "12")));
        histories.put("BOLLINGER", List.of(
                band("BOLLINGER", previousAt, "105", "100"),
                band("BOLLINGER", latestAt, "106", "100")));
        histories.put("KELTNER", List.of(
                band("KELTNER", previousAt, "104", "100"),
                band("KELTNER", latestAt, "104.8", "100")));
        histories.put("DONCHIAN", List.of(
                band("DONCHIAN", previousAt, "106", "100"),
                band("DONCHIAN", latestAt, "107.2", "100")));
        histories.put("OBV", List.of(
                result("OBV", previousAt, "value", "100"),
                result("OBV", latestAt, "value", "150")));
        histories.put("CMF", List.of(result("CMF", latestAt, "value", "0.2")));
        histories.put("VWAP", List.of(result("VWAP", latestAt, "value", "90")));

        return IntelligenceTestFixtures.context(candles, histories);
    }
}
