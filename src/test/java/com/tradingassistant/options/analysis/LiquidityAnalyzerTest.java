package com.tradingassistant.options.analysis;

import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.MarketDepth;
import com.tradingassistant.market.domain.MarketStatus;
import com.tradingassistant.market.domain.OptionContract;
import com.tradingassistant.market.domain.Quote;
import com.tradingassistant.options.OptionsTestFixtures;
import com.tradingassistant.options.config.OptionsAnalyticsConfig;
import com.tradingassistant.options.domain.ChainSnapshot;
import com.tradingassistant.options.domain.LiquidityState;
import com.tradingassistant.options.domain.LiquiditySummary;
import com.tradingassistant.options.domain.Tradability;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LiquidityAnalyzerTest {

    private final LiquidityAnalyzer analyzer = new LiquidityAnalyzer(OptionsTestFixtures.CONFIG);

    private ChainSnapshot chain(String spreadOffset, long openInterest, long volume) {
        List<OptionContract> contracts = OptionsTestFixtures.ladder(
                OptionsTestFixtures.UNDERLYING, OptionsTestFixtures.EXPIRY,
                List.of("490", "500", "510"));
        Map<InstrumentKey, Quote> quotes = new LinkedHashMap<>();
        for (OptionContract contract : contracts) {
            BigDecimal premium = OptionsTestFixtures.premium(contract, "500", "0.25",
                    OptionsTestFixtures.START);
            BigDecimal offset = new BigDecimal(spreadOffset);
            quotes.put(contract.instrument().key(),
                    OptionsTestFixtures.quote(contract, premium.toPlainString(), "0.10",
                            openInterest, volume, premium.subtract(offset).toPlainString(),
                            premium.add(offset).toPlainString()));
        }
        return OptionsTestFixtures.snapshot(contracts, Map.copyOf(quotes), "500");
    }

    @Test
    void reportsALiquidChainWithTightSpreadsAndInterest() {
        LiquiditySummary summary = analyzer.analyze(chain("0.01", 1_000L, 500L));

        assertThat(summary.status()).isEqualTo(AssessmentStatus.READY);
        assertThat(summary.state()).isEqualTo(LiquidityState.LIQUID);
        assertThat(summary.tradability()).isEqualTo(Tradability.HIGH);
        assertThat(summary.assessedLegs()).isEqualTo(6);
        assertThat(summary.wideSpreadLegs()).isZero();
        assertThat(summary.thinLegs()).isZero();
        assertThat(summary.averageSpreadPercent()).isPresent();
    }

    @Test
    void detectsWideSpreads() {
        LiquiditySummary summary = analyzer.analyze(chain("2.0", 1_000L, 500L));

        assertThat(summary.wideSpreadLegs()).isEqualTo(6);
        assertThat(summary.state()).isEqualTo(LiquidityState.ILLIQUID);
        assertThat(summary.tradability()).isEqualTo(Tradability.LOW);
    }

    @Test
    void detectsThinMarkets() {
        LiquiditySummary summary = analyzer.analyze(chain("0.05", 10L, 10L));

        assertThat(summary.thinLegs()).isEqualTo(6);
        assertThat(summary.state()).isEqualTo(LiquidityState.ILLIQUID);
    }

    @Test
    void honoursTheConfiguredSpreadThreshold() {
        LiquidityAnalyzer strict = new LiquidityAnalyzer(OptionsAnalyticsConfig.builder()
                .maximumSpreadPercent(new BigDecimal("0.1"))
                .build());

        LiquiditySummary summary = strict.analyze(chain("0.01", 1_000L, 500L));

        assertThat(summary.wideSpreadLegs()).isEqualTo(6);
        assertThat(summary.state()).isEqualTo(LiquidityState.ILLIQUID);
    }

    @Test
    void reportsUnknownWithoutTwoSidedQuotes() {
        List<OptionContract> contracts = OptionsTestFixtures.ladder(
                OptionsTestFixtures.UNDERLYING, OptionsTestFixtures.EXPIRY,
                List.of("500"));
        Map<InstrumentKey, Quote> quotes = new LinkedHashMap<>();
        for (OptionContract contract : contracts) {
            quotes.put(contract.instrument().key(), new Quote(contract.instrument().key(),
                    contract.instrument().tradingSymbol(), new BigDecimal("10"),
                    null, null, null, null, null, 100L, 100L, null,
                    BigDecimal.ZERO, null, null, null, OptionsTestFixtures.START,
                    MarketStatus.OPEN, null, null, null, null, MarketDepth.empty()));
        }
        ChainSnapshot snapshot = OptionsTestFixtures.snapshot(contracts, Map.copyOf(quotes),
                "500");

        LiquiditySummary summary = analyzer.analyze(snapshot);

        assertThat(summary.assessedLegs()).isZero();
        assertThat(summary.state()).isEqualTo(LiquidityState.UNKNOWN);
        assertThat(summary.tradability()).isEqualTo(Tradability.UNKNOWN);
        assertThat(summary.status()).isEqualTo(AssessmentStatus.INSUFFICIENT_DATA);
    }
}
