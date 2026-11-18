package com.tradingassistant.options.analysis;

import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.OptionContract;
import com.tradingassistant.market.domain.Quote;
import com.tradingassistant.options.OptionsTestFixtures;
import com.tradingassistant.options.config.OptionsAnalyticsConfig;
import com.tradingassistant.options.domain.ChainSnapshot;
import com.tradingassistant.options.domain.ChainStateSnapshot;
import com.tradingassistant.options.domain.OpenInterestBuildUp;
import com.tradingassistant.options.domain.OpenInterestSummary;
import com.tradingassistant.options.domain.StrikeOpenInterest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenInterestAnalyzerTest {

    private final OpenInterestAnalyzer analyzer = new OpenInterestAnalyzer(
            OptionsTestFixtures.CONFIG);

    private ChainSnapshot chain(List<String> strikes, Map<String, Long> callOpenInterest,
                                Map<String, Long> putOpenInterest, String netChange,
                                long volume) {
        List<OptionContract> contracts = OptionsTestFixtures.ladder(
                OptionsTestFixtures.UNDERLYING, OptionsTestFixtures.EXPIRY, strikes);
        Map<InstrumentKey, Quote> quotes = new LinkedHashMap<>();
        for (OptionContract contract : contracts) {
            long openInterest = (contract.optionType().isCall()
                    ? callOpenInterest.getOrDefault(contract.strike().toPlainString(), 0L)
                    : putOpenInterest.getOrDefault(contract.strike().toPlainString(), 0L));
            quotes.put(contract.instrument().key(),
                    OptionsTestFixtures.quote(contract, "5", netChange, openInterest, volume,
                            "0.05", "0.10"));
        }
        return OptionsTestFixtures.snapshot(contracts, Map.copyOf(quotes), "500");
    }

    @Test
    void aggregatesInterestAndLocatesConcentration() {
        ChainSnapshot snapshot = chain(List.of("480", "490", "500", "510", "520"),
                Map.of("480", 100L, "490", 100L, "500", 100L, "510", 100L, "520", 900L),
                Map.of("480", 1_200L, "490", 100L, "500", 100L, "510", 100L, "520", 100L),
                "1", 500L);

        OpenInterestSummary summary = analyzer.analyze(snapshot, ChainStateSnapshot.empty());

        assertThat(summary.totalCallOpenInterest()).isEqualTo(1_300L);
        assertThat(summary.totalPutOpenInterest()).isEqualTo(1_600L);
        assertThat(summary.totalCallVolume()).isEqualTo(2_500L);
        assertThat(summary.callConcentrationStrike()).contains(new BigDecimal("520"));
        assertThat(summary.putConcentrationStrike()).contains(new BigDecimal("480"));
        assertThat(summary.resistanceZones()).first()
                .isEqualTo(new BigDecimal("520"));
        assertThat(summary.supportZones()).first()
                .isEqualTo(new BigDecimal("480"));
        assertThat(summary.strikes()).hasSize(5);
        assertThat(summary.status()).isEqualTo(AssessmentStatus.PARTIAL);
    }

    @Test
    void describesChangeAgainstThePreviousCapture() {
        ChainSnapshot previous = chain(List.of("500"),
                Map.of("500", 200L), Map.of("500", 300L), "1", 100L);
        ChainSnapshot current = chain(List.of("500"),
                Map.of("500", 300L), Map.of("500", 250L), "1", 400L);

        OpenInterestSummary summary = analyzer.analyze(current,
                ChainStateSnapshot.of(previous));

        StrikeOpenInterest strike = summary.strikes().get(0);
        assertThat(strike.callOpenInterestChange()).contains(100L);
        assertThat(strike.putOpenInterestChange()).contains(-50L);
        assertThat(strike.callVolumeChange()).contains(300L);
        assertThat(summary.status()).isEqualTo(AssessmentStatus.READY);
    }

    @Test
    void classifiesPositioningFromPriceAndInterestDirection() {
        assertThat(callBuildUp("2", 100L, 150L)).isEqualTo(OpenInterestBuildUp.LONG_BUILD_UP);
        assertThat(callBuildUp("-2", 100L, 150L)).isEqualTo(OpenInterestBuildUp.SHORT_BUILD_UP);
        assertThat(callBuildUp("-2", 200L, 150L)).isEqualTo(OpenInterestBuildUp.LONG_UNWINDING);
        assertThat(callBuildUp("2", 200L, 150L)).isEqualTo(OpenInterestBuildUp.SHORT_COVERING);
        assertThat(callBuildUp("0", 200L, 150L)).isEqualTo(OpenInterestBuildUp.NEUTRAL);
        assertThat(callBuildUp("2", 200L, 200L)).isEqualTo(OpenInterestBuildUp.NEUTRAL);
    }

    private OpenInterestBuildUp callBuildUp(String netChange, long previousInterest,
                                            long currentInterest) {
        ChainSnapshot previous = chain(List.of("500"), Map.of("500", previousInterest),
                Map.of("500", 0L), "0", 10L);
        ChainSnapshot current = chain(List.of("500"), Map.of("500", currentInterest),
                Map.of("500", 0L), netChange, 10L);
        return analyzer.analyze(current, ChainStateSnapshot.of(previous))
                .strikes().get(0).callBuildUp();
    }

    @Test
    void reportsUnknownBuildUpWithoutAPreviousCapture() {
        ChainSnapshot snapshot = chain(List.of("500"), Map.of("500", 100L),
                Map.of("500", 100L), "1", 100L);

        OpenInterestSummary summary = analyzer.analyze(snapshot, ChainStateSnapshot.empty());

        assertThat(summary.strikes().get(0).callBuildUp())
                .isEqualTo(OpenInterestBuildUp.UNKNOWN);
        assertThat(summary.strikes().get(0).callOpenInterestChange()).isEmpty();
    }

    @Test
    void reportsInsufficientDataWithoutInterestOrVolume() {
        ChainSnapshot snapshot = chain(List.of("500"), Map.of("500", 0L),
                Map.of("500", 0L), "0", 0L);

        OpenInterestSummary summary = analyzer.analyze(snapshot, ChainStateSnapshot.empty());

        assertThat(summary.status()).isEqualTo(AssessmentStatus.INSUFFICIENT_DATA);
        assertThat(summary.callConcentrationStrike()).isEmpty();
    }

    @Test
    void limitsZonesToTheConfiguredCount() {
        OptionsAnalyticsConfig config = OptionsAnalyticsConfig.builder()
                .supportResistanceStrikeCount(2)
                .build();
        OpenInterestAnalyzer limited = new OpenInterestAnalyzer(config);
        ChainSnapshot snapshot = chain(List.of("480", "490", "500", "510", "520"),
                Map.of("480", 10L, "490", 20L, "500", 30L, "510", 40L, "520", 50L),
                Map.of("480", 50L, "490", 40L, "500", 30L, "510", 20L, "520", 10L),
                "1", 10L);

        OpenInterestSummary summary = limited.analyze(snapshot, ChainStateSnapshot.empty());

        assertThat(summary.resistanceZones()).hasSize(2)
                .containsExactly(new BigDecimal("520"), new BigDecimal("510"));
        assertThat(summary.supportZones()).hasSize(2)
                .containsExactly(new BigDecimal("480"), new BigDecimal("490"));
    }
}
