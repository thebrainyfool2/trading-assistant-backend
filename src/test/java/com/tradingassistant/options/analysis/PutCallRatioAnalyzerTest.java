package com.tradingassistant.options.analysis;

import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.OptionContract;
import com.tradingassistant.market.domain.Quote;
import com.tradingassistant.options.OptionsTestFixtures;
import com.tradingassistant.options.config.OptionsAnalyticsConfig;
import com.tradingassistant.options.domain.ChainSnapshot;
import com.tradingassistant.options.domain.PcrAggregation;
import com.tradingassistant.options.domain.PutCallRatioSummary;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PutCallRatioAnalyzerTest {

    private final PutCallRatioAnalyzer analyzer = new PutCallRatioAnalyzer(
            OptionsTestFixtures.CONFIG);

    private ChainSnapshot chain(long callInterest, long putInterest, long callVolume,
                                long putVolume, LocalDate... expiries) {
        List<OptionContract> contracts = new ArrayList<>();
        for (LocalDate expiry : expiries) {
            contracts.addAll(OptionsTestFixtures.ladder(OptionsTestFixtures.UNDERLYING, expiry,
                    List.of("500")));
        }
        Map<InstrumentKey, Quote> quotes = new LinkedHashMap<>();
        for (OptionContract contract : contracts) {
            boolean call = contract.optionType().isCall();
            long interest = call ? callInterest : putInterest;
            long volume = call ? callVolume : putVolume;
            quotes.put(contract.instrument().key(),
                    OptionsTestFixtures.quote(contract, "5", "0.50", interest, volume,
                            "0.05", "0.10"));
        }
        return OptionsTestFixtures.snapshot(contracts, Map.copyOf(quotes), "500");
    }

    @Test
    void computesChainRatiosByInterestAndVolume() {
        PutCallRatioSummary summary = analyzer.analyze(chain(400L, 600L, 200L, 300L,
                OptionsTestFixtures.EXPIRY));

        assertThat(summary.chainOpenInterestRatio()).isPresent();
        assertThat(summary.chainOpenInterestRatio().orElseThrow().doubleValue())
                .isCloseTo(1.5, within(1.0e-9));
        assertThat(summary.chainVolumeRatio().orElseThrow().doubleValue())
                .isCloseTo(1.5, within(1.0e-9));
        assertThat(summary.status()).isEqualTo(AssessmentStatus.READY);
    }

    @Test
    void reportsPerExpiryRatiosWhenAggregationRequestsThem() {
        PutCallRatioSummary summary = analyzer.analyze(chain(100L, 200L, 50L, 150L,
                OptionsTestFixtures.EXPIRY, OptionsTestFixtures.NEXT_EXPIRY));

        assertThat(summary.expiries()).hasSize(2);
        assertThat(summary.expiries()).allSatisfy(entry -> {
            assertThat(entry.openInterestRatio()).contains(new java.math.BigDecimal("2"));
            assertThat(entry.volumeRatio()).contains(new java.math.BigDecimal("3"));
        });
    }

    @Test
    void omitsPerExpiryRatiosWhenAggregationIsChainOnly() {
        PutCallRatioAnalyzer chainOnly = new PutCallRatioAnalyzer(
                OptionsAnalyticsConfig.builder().pcrAggregation(PcrAggregation.CHAIN).build());

        PutCallRatioSummary summary = chainOnly.analyze(chain(100L, 200L, 50L, 150L,
                OptionsTestFixtures.EXPIRY, OptionsTestFixtures.NEXT_EXPIRY));

        assertThat(summary.expiries()).isEmpty();
        assertThat(summary.aggregation()).isEqualTo(PcrAggregation.CHAIN);
        assertThat(summary.chainOpenInterestRatio()).isPresent();
    }

    @Test
    void omitsChainRatiosWhenAggregationIsExpiryOnly() {
        PutCallRatioAnalyzer expiryOnly = new PutCallRatioAnalyzer(
                OptionsAnalyticsConfig.builder().pcrAggregation(PcrAggregation.EXPIRY).build());

        PutCallRatioSummary summary = expiryOnly.analyze(chain(100L, 200L, 50L, 150L,
                OptionsTestFixtures.EXPIRY));

        assertThat(summary.chainOpenInterestRatio()).isEmpty();
        assertThat(summary.chainVolumeRatio()).isEmpty();
        assertThat(summary.expiries()).hasSize(1);
    }

    @Test
    void reportsEmptyRatiosWhenTheCallSideIsZero() {
        PutCallRatioSummary summary = analyzer.analyze(chain(0L, 500L, 0L, 400L,
                OptionsTestFixtures.EXPIRY));

        assertThat(summary.chainOpenInterestRatio()).isEmpty();
        assertThat(summary.chainVolumeRatio()).isEmpty();
        assertThat(summary.status()).isEqualTo(AssessmentStatus.INSUFFICIENT_DATA);
    }
}
