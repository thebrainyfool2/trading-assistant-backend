package com.tradingassistant.options.volatility;

import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.OptionContract;
import com.tradingassistant.market.domain.Quote;
import com.tradingassistant.options.OptionsTestFixtures;
import com.tradingassistant.options.domain.ChainSnapshot;
import com.tradingassistant.options.domain.OptionLegKey;
import com.tradingassistant.options.domain.VolatilityState;
import com.tradingassistant.options.domain.VolatilitySummary;
import com.tradingassistant.options.domain.VolatilityTrend;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class VolatilityAnalyzerTest {

    private final VolatilityAnalyzer analyzer = new VolatilityAnalyzer(OptionsTestFixtures.CONFIG);
    private final ImpliedVolatilityResolver resolver = new ImpliedVolatilityResolver(
            new ImpliedVolatilitySolver(OptionsTestFixtures.CONFIG));

    private ChainSnapshot chain(Function<BigDecimal, String> volatilityByStrike,
                                LocalDate... expiries) {
        List<OptionContract> contracts = new ArrayList<>();
        List<String> strikes = OptionsTestFixtures.strikes("460", "540", "10");
        for (LocalDate expiry : expiries) {
            contracts.addAll(OptionsTestFixtures.ladder(OptionsTestFixtures.UNDERLYING, expiry,
                    strikes));
        }
        Map<InstrumentKey, Quote> quotes = new LinkedHashMap<>();
        for (OptionContract contract : contracts) {
            BigDecimal premium = OptionsTestFixtures.premium(contract, "500",
                    volatilityByStrike.apply(contract.strike()), OptionsTestFixtures.START);
            quotes.put(contract.instrument().key(),
                    OptionsTestFixtures.quote(contract, premium.toPlainString(), "0.50",
                            1_000L, 500L, "0.05", "0.10"));
        }
        return OptionsTestFixtures.snapshot(contracts, Map.copyOf(quotes), "500");
    }

    @Test
    void describesAtmVolatilityAndSurface() {
        ChainSnapshot snapshot = chain(strike -> "0.25", OptionsTestFixtures.EXPIRY);

        VolatilitySummary summary = analyzer.analyze(snapshot, resolver.resolve(snapshot), null);

        assertThat(summary.status()).isEqualTo(AssessmentStatus.READY);
        assertThat(summary.atmImpliedVolatility()).isPresent();
        assertThat(summary.atmImpliedVolatility().orElseThrow().doubleValue())
                .isCloseTo(0.25, within(1.0e-3));
        assertThat(summary.surface()).hasSize(18);
        assertThat(summary.smile()).isNotEmpty();
        assertThat(summary.trend()).isEqualTo(VolatilityTrend.UNKNOWN);
        assertThat(summary.state()).isEqualTo(VolatilityState.UNKNOWN);
    }

    @Test
    void derivesChangeRankAndTrendFromHistory() {
        ChainSnapshot snapshot = chain(strike -> "0.25", OptionsTestFixtures.EXPIRY);
        ImpliedVolatilityHistory history = new ImpliedVolatilityHistory(10);
        history.record(OptionsTestFixtures.START.minusSeconds(120), new BigDecimal("0.15"));
        history.record(OptionsTestFixtures.START.minusSeconds(60), new BigDecimal("0.20"));

        VolatilitySummary summary = analyzer.analyze(snapshot, resolver.resolve(snapshot),
                history);

        assertThat(summary.impliedVolatilityChange()).isPresent();
        assertThat(summary.impliedVolatilityChange().orElseThrow().doubleValue())
                .isCloseTo(0.05, within(1.0e-3));
        assertThat(summary.impliedVolatilityRank().orElseThrow().doubleValue())
                .isCloseTo(100.0, within(1.0e-6));
        assertThat(summary.trend()).isEqualTo(VolatilityTrend.RISING);
        assertThat(summary.state()).isEqualTo(VolatilityState.EXPANDING);
        assertThat(history.size()).isEqualTo(3);
    }

    @Test
    void computesPositiveSkewWhenOutOfTheMoneyPutsAreRicher() {
        ChainSnapshot snapshot = chain(strike -> {
            int comparison = strike.compareTo(new BigDecimal("500"));
            if (comparison < 0) {
                return "0.30";
            }
            return comparison > 0 ? "0.20" : "0.25";
        }, OptionsTestFixtures.EXPIRY);

        VolatilitySummary summary = analyzer.analyze(snapshot, resolver.resolve(snapshot), null);

        assertThat(summary.skew()).isPresent();
        assertThat(summary.skew().orElseThrow().doubleValue()).isGreaterThan(0.05);
    }

    @Test
    void reportsInsufficientDataWhenNoPremiumIsSolvable() {
        List<OptionContract> contracts = OptionsTestFixtures.ladder(OptionsTestFixtures.UNDERLYING,
                OptionsTestFixtures.EXPIRY, List.of("490", "500", "510"));
        Map<InstrumentKey, Quote> quotes = new LinkedHashMap<>();
        for (OptionContract contract : contracts) {
            quotes.put(contract.instrument().key(), OptionsTestFixtures.quote(contract, "0",
                    "0", 1_000L, 500L, "0.05", "0.10"));
        }
        ChainSnapshot snapshot = OptionsTestFixtures.snapshot(contracts, Map.copyOf(quotes),
                "500");

        VolatilitySummary summary = analyzer.analyze(snapshot, resolver.resolve(snapshot), null);

        assertThat(summary.status()).isEqualTo(AssessmentStatus.INSUFFICIENT_DATA);
        assertThat(summary.atmImpliedVolatility()).isEmpty();
        assertThat(summary.surface()).isEmpty();
    }

    @Test
    void marksPartialWhenAPricedLegCannotBeSolved() {
        List<OptionContract> contracts = OptionsTestFixtures.ladder(OptionsTestFixtures.UNDERLYING,
                OptionsTestFixtures.EXPIRY, List.of("490", "500", "510"));
        Map<InstrumentKey, Quote> quotes = new LinkedHashMap<>();
        for (OptionContract contract : contracts) {
            String premium = contract.strike().compareTo(new BigDecimal("490")) == 0
                    && contract.optionType().isCall()
                    ? "5000"
                    : OptionsTestFixtures.premium(contract, "500", "0.25",
                            OptionsTestFixtures.START).toPlainString();
            quotes.put(contract.instrument().key(), OptionsTestFixtures.quote(contract, premium,
                    "0.50", 1_000L, 500L, "0.05", "0.10"));
        }
        ChainSnapshot snapshot = OptionsTestFixtures.snapshot(contracts, Map.copyOf(quotes),
                "500");

        VolatilitySummary summary = analyzer.analyze(snapshot, resolver.resolve(snapshot), null);

        assertThat(summary.status()).isEqualTo(AssessmentStatus.PARTIAL);
        assertThat(summary.surface()).hasSize(5);
    }

    @Test
    void preparesSurfaceAcrossExpiries() {
        ChainSnapshot snapshot = chain(strike -> "0.25", OptionsTestFixtures.EXPIRY,
                OptionsTestFixtures.NEXT_EXPIRY);

        VolatilitySummary summary = analyzer.analyze(snapshot, resolver.resolve(snapshot), null);

        assertThat(summary.surface()).extracting(point -> point.expiry())
                .containsOnly(OptionsTestFixtures.EXPIRY, OptionsTestFixtures.NEXT_EXPIRY);
        assertThat(summary.surface()).hasSize(36);
        assertThat(summary.smile()).extracting(point -> point.strike())
                .doesNotContain(new BigDecimal("540"));
    }

    @Test
    void resolvesEveryLegToTheVolatilityThatProducedIt() {
        ChainSnapshot snapshot = chain(strike -> "0.30", OptionsTestFixtures.EXPIRY);

        Map<OptionLegKey, BigDecimal> resolved = resolver.resolve(snapshot);

        assertThat(resolved).hasSize(18);
        assertThat(resolved.values()).allSatisfy(volatility ->
                assertThat(volatility.doubleValue()).isCloseTo(0.30, within(1.0e-3)));
    }
}
