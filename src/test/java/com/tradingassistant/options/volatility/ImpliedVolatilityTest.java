package com.tradingassistant.options.volatility;

import com.tradingassistant.market.domain.OptionType;
import com.tradingassistant.options.OptionsTestFixtures;
import com.tradingassistant.options.config.OptionsAnalyticsConfig;
import com.tradingassistant.options.greeks.BlackScholesModel;
import com.tradingassistant.options.math.ExpiryTime;
import com.tradingassistant.options.exception.OptionsValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ImpliedVolatilityTest {

    private final OptionsAnalyticsConfig config = OptionsTestFixtures.CONFIG;
    private final ImpliedVolatilitySolver solver = new ImpliedVolatilitySolver(config);
    private final BlackScholesModel model = new BlackScholesModel();

    private BigDecimal premium(OptionType type, String strike, String volatility) {
        BigDecimal years = ExpiryTime.yearsToExpiry(OptionsTestFixtures.EXPIRY,
                OptionsTestFixtures.START, config.exchangeZone(), config.dayCount());
        return model.price(type, new BigDecimal("500"), new BigDecimal(strike),
                config.riskFreeRate(), config.dividendYield(), new BigDecimal(volatility),
                years, config.dayCount());
    }

    @Test
    void recoversCallVolatilityFromItsPremium() {
        BigDecimal premium = premium(OptionType.CALL, "500", "0.25");

        Optional<BigDecimal> solved = solver.solve(OptionType.CALL, new BigDecimal("500"),
                new BigDecimal("500"), OptionsTestFixtures.EXPIRY, OptionsTestFixtures.START,
                premium);

        assertThat(solved).isPresent();
        assertThat(solved.orElseThrow().doubleValue()).isCloseTo(0.25, within(1.0e-4));
    }

    @Test
    void recoversPutVolatilityFromItsPremium() {
        BigDecimal premium = premium(OptionType.PUT, "480", "0.35");

        Optional<BigDecimal> solved = solver.solve(OptionType.PUT, new BigDecimal("500"),
                new BigDecimal("480"), OptionsTestFixtures.EXPIRY, OptionsTestFixtures.START,
                premium);

        assertThat(solved).isPresent();
        assertThat(solved.orElseThrow().doubleValue()).isCloseTo(0.35, within(1.0e-4));
    }

    @Test
    void returnsEmptyWhenNoVolatilityCanReproduceThePremium() {
        assertThat(solver.solve(OptionType.CALL, new BigDecimal("500"), new BigDecimal("500"),
                OptionsTestFixtures.EXPIRY, OptionsTestFixtures.START,
                new BigDecimal("5000"))).isEmpty();
        assertThat(solver.solve(OptionType.CALL, new BigDecimal("500"), new BigDecimal("500"),
                OptionsTestFixtures.EXPIRY, OptionsTestFixtures.START, BigDecimal.ZERO)).isEmpty();
    }

    @Test
    void returnsEmptyOnTheExpiryDay() {
        assertThat(solver.solve(OptionType.CALL, new BigDecimal("500"), new BigDecimal("500"),
                OptionsTestFixtures.expiryInDays(0), OptionsTestFixtures.START,
                new BigDecimal("5"))).isEmpty();
    }

    @Test
    void providesChangeRankAndPercentileFromHistory() {
        ImpliedVolatilityHistory history = new ImpliedVolatilityHistory(10);
        Instant first = OptionsTestFixtures.START;
        history.record(first, new BigDecimal("0.20"));
        history.record(first.plus(1, ChronoUnit.MINUTES), new BigDecimal("0.30"));
        history.record(first.plus(2, ChronoUnit.MINUTES), new BigDecimal("0.40"));
        history.record(first.plus(3, ChronoUnit.MINUTES), new BigDecimal("0.50"));

        assertThat(history.current()).contains(new BigDecimal("0.50"));
        assertThat(history.change()).contains(new BigDecimal("0.10"));
        assertThat(history.rank()).isPresent();
        assertThat(history.rank().orElseThrow().doubleValue()).isCloseTo(100.0, within(1.0e-9));
        assertThat(history.percentile().orElseThrow().doubleValue())
                .isCloseTo(75.0, within(1.0e-9));
    }

    @Test
    void reportsEmptyStatisticsUntilTwoObservationsExist() {
        ImpliedVolatilityHistory history = new ImpliedVolatilityHistory(5);

        assertThat(history.change()).isEmpty();
        assertThat(history.rank()).isEmpty();
        assertThat(history.percentile()).isEmpty();

        history.record(OptionsTestFixtures.START, new BigDecimal("0.25"));

        assertThat(history.change()).isEmpty();
        assertThat(history.rank()).isEmpty();
        assertThat(history.size()).isEqualTo(1);
    }

    @Test
    void boundsHistoryToItsWindow() {
        ImpliedVolatilityHistory history = new ImpliedVolatilityHistory(3);
        for (int index = 0; index < 6; index++) {
            history.record(OptionsTestFixtures.START.plus(index, ChronoUnit.MINUTES),
                    new BigDecimal("0." + (20 + index)));
        }

        assertThat(history.size()).isEqualTo(3);
        assertThat(history.snapshot()).extracting(ImpliedVolatilityHistory.Observation::impliedVolatility)
                .containsExactly(new BigDecimal("0.23"), new BigDecimal("0.24"),
                        new BigDecimal("0.25"));
    }

    @Test
    void replacesTheLastObservationAtTheSameInstant() {
        ImpliedVolatilityHistory history = new ImpliedVolatilityHistory(5);
        history.record(OptionsTestFixtures.START, new BigDecimal("0.20"));
        history.record(OptionsTestFixtures.START, new BigDecimal("0.24"));

        assertThat(history.size()).isEqualTo(1);
        assertThat(history.current()).contains(new BigDecimal("0.24"));
    }

    @Test
    void rejectsObservationsThatMoveBackwardsInTime() {
        ImpliedVolatilityHistory history = new ImpliedVolatilityHistory(5);
        history.record(OptionsTestFixtures.START.plus(5, ChronoUnit.MINUTES),
                new BigDecimal("0.20"));

        assertThatThrownBy(() -> history.record(OptionsTestFixtures.START,
                new BigDecimal("0.24")))
                .isInstanceOf(OptionsValidationException.class)
                .hasMessageContaining("backwards");
    }

    @Test
    void rejectsNonPositiveObservations() {
        ImpliedVolatilityHistory history = new ImpliedVolatilityHistory(5);

        assertThatThrownBy(() -> history.record(OptionsTestFixtures.START, BigDecimal.ZERO))
                .isInstanceOf(OptionsValidationException.class)
                .hasMessageContaining("must be positive");
    }
}
