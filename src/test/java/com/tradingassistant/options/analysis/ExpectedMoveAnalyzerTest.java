package com.tradingassistant.options.analysis;

import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.OptionContract;
import com.tradingassistant.market.domain.Quote;
import com.tradingassistant.options.OptionsTestFixtures;
import com.tradingassistant.options.config.OptionsAnalyticsConfig;
import com.tradingassistant.options.domain.ChainSnapshot;
import com.tradingassistant.options.domain.ExpectedMoveMethod;
import com.tradingassistant.options.domain.ExpectedMoveSummary;
import com.tradingassistant.options.domain.OptionLegKey;
import com.tradingassistant.options.volatility.ImpliedVolatilityResolver;
import com.tradingassistant.options.volatility.ImpliedVolatilitySolver;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class ExpectedMoveAnalyzerTest {

    private final OptionsAnalyticsConfig config = OptionsTestFixtures.CONFIG;
    private final ExpectedMoveAnalyzer analyzer = new ExpectedMoveAnalyzer(config);
    private final ImpliedVolatilityResolver resolver = new ImpliedVolatilityResolver(
            new ImpliedVolatilitySolver(config));

    private ChainSnapshot chain(Function<BigDecimal, String> volatilityByStrike,
                                OptionsAnalyticsConfig settings) {
        List<OptionContract> contracts = OptionsTestFixtures.ladder(
                OptionsTestFixtures.UNDERLYING, OptionsTestFixtures.EXPIRY,
                List.of("490", "500", "510"));
        Map<InstrumentKey, Quote> quotes = new LinkedHashMap<>();
        for (OptionContract contract : contracts) {
            BigDecimal premium = OptionsTestFixtures.premium(contract, "500",
                    volatilityByStrike.apply(contract.strike()), OptionsTestFixtures.START);
            quotes.put(contract.instrument().key(),
                    OptionsTestFixtures.quote(contract, premium.toPlainString(), "0.10",
                            1_000L, 500L, "0.05", "0.10"));
        }
        return new com.tradingassistant.options.chain.OptionChainBuilder(settings)
                .build(OptionsTestFixtures.SPOT, OptionsTestFixtures.UNDERLYING,
                        new BigDecimal("500"), OptionsTestFixtures.START, contracts,
                        Map.copyOf(quotes));
    }

    private Map<OptionLegKey, BigDecimal> volatilities(ChainSnapshot snapshot) {
        return resolver.resolve(snapshot);
    }

    @Test
    void describesExpiryDailyAndWeeklyMovesFromAtmVolatility() {
        ChainSnapshot snapshot = chain(strike -> "0.25", config);

        ExpectedMoveSummary summary = analyzer.analyze(snapshot, volatilities(snapshot));

        assertThat(summary.status()).isEqualTo(AssessmentStatus.READY);
        assertThat(summary.method()).isEqualTo(ExpectedMoveMethod.VOLATILITY);
        assertThat(summary.expiryMove()).isPresent();
        double move = summary.expiryMove().orElseThrow().doubleValue();
        assertThat(move).isBetween(15.0, 22.0);
        assertThat(summary.dailyMove().orElseThrow().doubleValue()).isLessThan(move);
        assertThat(summary.weeklyMove().orElseThrow().doubleValue()).isLessThan(move);
        assertThat(summary.upperBound().orElseThrow().doubleValue())
                .isCloseTo(500.0 + move, org.assertj.core.api.Assertions.within(1.0e-6));
        assertThat(summary.lowerBound().orElseThrow().doubleValue())
                .isCloseTo(500.0 - move, org.assertj.core.api.Assertions.within(1.0e-6));
    }

    @Test
    void usesTheAtmStraddleWhenConfigured() {
        OptionsAnalyticsConfig straddle = OptionsAnalyticsConfig.builder()
                .expectedMoveMethod(ExpectedMoveMethod.STRADDLE)
                .build();
        ExpectedMoveAnalyzer straddleAnalyzer = new ExpectedMoveAnalyzer(straddle);
        ChainSnapshot snapshot = chain(strike -> "0.25", straddle);

        ExpectedMoveSummary summary = straddleAnalyzer.analyze(snapshot, volatilities(snapshot));

        assertThat(summary.method()).isEqualTo(ExpectedMoveMethod.STRADDLE);
        assertThat(summary.status()).isEqualTo(AssessmentStatus.READY);
        assertThat(summary.expiryMove()).isPresent();
        assertThat(summary.expiryMove().orElseThrow()).isPositive();
        assertThat(summary.upperBound().orElseThrow())
                .isEqualByComparingTo(new BigDecimal("500").add(
                        summary.expiryMove().orElseThrow()));
    }

    @Test
    void reportsUnavailableMoveWithoutVolatility() {
        List<OptionContract> contracts = OptionsTestFixtures.ladder(
                OptionsTestFixtures.UNDERLYING, OptionsTestFixtures.EXPIRY,
                List.of("490", "500", "510"));
        Map<InstrumentKey, Quote> quotes = new LinkedHashMap<>();
        for (OptionContract contract : contracts) {
            quotes.put(contract.instrument().key(),
                    OptionsTestFixtures.quote(contract, "0", "0", 100L, 100L,
                            "0.05", "0.10"));
        }
        ChainSnapshot snapshot = OptionsTestFixtures.snapshot(contracts, Map.copyOf(quotes),
                "500");

        ExpectedMoveSummary summary = analyzer.analyze(snapshot, volatilities(snapshot));

        assertThat(summary.status()).isEqualTo(AssessmentStatus.PARTIAL);
        assertThat(summary.expiryMove()).isEmpty();
        assertThat(summary.upperBound()).isEmpty();
    }
}
