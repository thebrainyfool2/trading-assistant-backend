package com.tradingassistant.options.analysis;

import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.OptionContract;
import com.tradingassistant.market.domain.Quote;
import com.tradingassistant.options.OptionsTestFixtures;
import com.tradingassistant.options.config.OptionsAnalyticsConfig;
import com.tradingassistant.options.domain.ChainSnapshot;
import com.tradingassistant.options.domain.MaxPainSummary;
import com.tradingassistant.options.domain.PainPoint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MaxPainAnalyzerTest {

    private final MaxPainAnalyzer analyzer = new MaxPainAnalyzer(OptionsTestFixtures.CONFIG);

    private ChainSnapshot chain(List<String> strikes, long openInterest) {
        List<OptionContract> contracts = OptionsTestFixtures.ladder(
                OptionsTestFixtures.UNDERLYING, OptionsTestFixtures.EXPIRY, strikes);
        Map<InstrumentKey, Quote> quotes = new LinkedHashMap<>();
        for (OptionContract contract : contracts) {
            quotes.put(contract.instrument().key(),
                    OptionsTestFixtures.quote(contract, "5", "0", openInterest, 100L,
                            "0.05", "0.10"));
        }
        return OptionsTestFixtures.snapshot(contracts, Map.copyOf(quotes), "500");
    }

    @Test
    void locatesMaxPainAtTheBalancePointOfASymmetricChain() {
        MaxPainSummary summary = analyzer.analyze(chain(
                List.of("480", "490", "500", "510", "520"), 100L));

        assertThat(summary.status()).isEqualTo(AssessmentStatus.READY);
        assertThat(summary.maxPainStrike()).contains(new BigDecimal("500"));
        assertThat(summary.expiry()).contains(OptionsTestFixtures.EXPIRY);
        assertThat(summary.curve()).hasSize(5);
        assertThat(summary.nearestStrikes()).extracting(PainPoint::strike)
                .contains(new BigDecimal("500"));
    }

    @Test
    void painCurveIsMinimalAtTheReportedStrike() {
        MaxPainSummary summary = analyzer.analyze(chain(
                List.of("480", "490", "500", "510", "520"), 100L));
        BigDecimal reported = summary.maxPainStrike().orElseThrow();
        BigDecimal minimum = summary.curve().stream()
                .map(PainPoint::totalPain)
                .min(BigDecimal::compareTo)
                .orElseThrow();

        assertThat(summary.curve()).filteredOn(point -> point.strike().compareTo(reported) == 0)
                .singleElement()
                .satisfies(point -> assertThat(point.totalPain())
                        .isEqualByComparingTo(minimum));
    }

    @Test
    void computesIntrinsicPainFromCallsBelowAndPutsAboveTheCandidate() {
        MaxPainSummary summary = analyzer.analyze(chain(List.of("480", "500", "520"), 100L));

        PainPoint at500 = summary.curve().stream()
                .filter(point -> point.strike().compareTo(new BigDecimal("500")) == 0)
                .findFirst()
                .orElseThrow();
        assertThat(at500.callPain()).isEqualByComparingTo(new BigDecimal("2000"));
        assertThat(at500.putPain()).isEqualByComparingTo(new BigDecimal("2000"));
        assertThat(at500.totalPain()).isEqualByComparingTo(new BigDecimal("4000"));
    }

    @Test
    void limitsNearestStrikesToTheConfiguredCount() {
        MaxPainAnalyzer limited = new MaxPainAnalyzer(OptionsAnalyticsConfig.builder()
                .maxPainNearestStrikeCount(1)
                .build());

        MaxPainSummary summary = limited.analyze(chain(
                List.of("480", "490", "500", "510", "520"), 100L));

        assertThat(summary.nearestStrikes()).hasSize(1);
    }

    @Test
    void reportsInsufficientDataWithoutOpenInterest() {
        MaxPainSummary summary = analyzer.analyze(chain(List.of("490", "500", "510"), 0L));

        assertThat(summary.status()).isEqualTo(AssessmentStatus.INSUFFICIENT_DATA);
        assertThat(summary.maxPainStrike()).isEmpty();
        assertThat(summary.curve()).isEmpty();
    }
}
