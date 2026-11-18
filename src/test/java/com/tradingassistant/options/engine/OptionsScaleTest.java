package com.tradingassistant.options.engine;

import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.OptionContract;
import com.tradingassistant.market.domain.Quote;
import com.tradingassistant.options.OptionsTestFixtures;
import com.tradingassistant.options.domain.ChainSnapshot;
import com.tradingassistant.options.domain.OptionsAnalysisContext;
import com.tradingassistant.options.domain.OptionsAssessment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OptionsScaleTest {

    @Test
    void analysesAThousandContractChainWithinABoundedTime() {
        List<OptionContract> contracts = new ArrayList<>();
        List<String> strikes = OptionsTestFixtures.strikes("480", "520", "1");
        for (int week = 1; week <= 5; week++) {
            LocalDate expiry = OptionsTestFixtures.expiryInDays(week * 7L);
            contracts.addAll(OptionsTestFixtures.ladder(OptionsTestFixtures.UNDERLYING, expiry,
                    strikes));
        }
        Map<InstrumentKey, Quote> quotes = new LinkedHashMap<>();
        List<String> quoteStrikes = OptionsTestFixtures.strikes("490", "510", "1");
        for (OptionContract contract : contracts) {
            String premium = quoteStrikes.contains(contract.strike().toPlainString())
                    ? OptionsTestFixtures.premium(contract, "500", "0.25",
                            OptionsTestFixtures.START).toPlainString()
                    : "0";
            quotes.put(contract.instrument().key(),
                    OptionsTestFixtures.quote(contract, premium, "0.10", 1_000L, 500L,
                            "0.05", "0.10"));
        }
        ChainSnapshot snapshot = OptionsTestFixtures.snapshot(contracts, Map.copyOf(quotes),
                "500");
        OptionsAnalyticsEngine engine = OptionsAnalyticsEngine.standard(
                OptionsTestFixtures.CONFIG);

        Instant started = Instant.now();
        OptionsAssessment assessment = engine.analyze(OptionsAnalysisContext.of(snapshot,
                OptionsTestFixtures.marketAssessment(OptionsTestFixtures.START)));
        Duration elapsed = Duration.between(started, Instant.now());

        assertThat(snapshot.chain().legCount()).isEqualTo(410);
        assertThat(snapshot.expiries()).hasSize(5);
        assertThat(assessment.chain().legCount()).isEqualTo(410);
        assertThat(assessment.diagnostics()).containsEntry("expiries", "5");
        assertThat(assessment.status()).isNotNull();
        assertThat(elapsed).isLessThan(Duration.ofSeconds(20));
    }

    @Test
    void keepsAssessmentImmutable() {
        OptionsAssessment assessment = OptionsAnalyticsEngine.standard(
                        OptionsTestFixtures.CONFIG)
                .analyze(OptionsAnalysisContext.of(OptionsTestFixtures.defaultSnapshot(),
                        OptionsTestFixtures.marketAssessment(OptionsTestFixtures.START)));

        assertThat(assessment.volatility().surface()).isNotNull();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> assessment.volatility().surface().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(assessment.diagnostics()).isNotNull();
        assertThat(assessment.greeks().netExposure().delta()).isNotNull();
        assertThat(assessment.chain().spotPrice()).isEqualByComparingTo(new BigDecimal("500"));
    }
}
