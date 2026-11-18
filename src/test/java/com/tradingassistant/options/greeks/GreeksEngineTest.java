package com.tradingassistant.options.greeks;

import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.market.domain.OptionType;
import com.tradingassistant.options.OptionsTestFixtures;
import com.tradingassistant.options.domain.Greeks;
import com.tradingassistant.options.domain.GreeksAssessment;
import com.tradingassistant.options.exception.OptionsValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class GreeksEngineTest {

    private final GreeksEngine engine = new GreeksEngine(OptionsTestFixtures.CONFIG);

    @Test
    void producesPlausibleAtTheMoneySensitivities() {
        GreeksAssessment assessment = engine.evaluate(OptionType.CALL, new BigDecimal("500"),
                new BigDecimal("500"), OptionsTestFixtures.EXPIRY, OptionsTestFixtures.START,
                new BigDecimal("0.25"));

        assertThat(assessment.status()).isEqualTo(AssessmentStatus.READY);
        assertThat(assessment.greeks()).isPresent();
        Greeks greeks = assessment.greeks().orElseThrow();
        assertThat(greeks.delta()).isBetween(new BigDecimal("0.45"), new BigDecimal("0.65"));
        assertThat(greeks.gamma()).isPositive();
        assertThat(greeks.vega()).isPositive();
        assertThat(greeks.theta()).isNegative();
        assertThat(assessment.model()).isEqualTo(BlackScholesModel.NAME);
    }

    @Test
    void callAndPutDeltaDifferByOneWithoutDividends() {
        Greeks call = engine.evaluate(OptionType.CALL, new BigDecimal("500"),
                new BigDecimal("480"), OptionsTestFixtures.EXPIRY, OptionsTestFixtures.START,
                new BigDecimal("0.22")).greeks().orElseThrow();
        Greeks put = engine.evaluate(OptionType.PUT, new BigDecimal("500"),
                new BigDecimal("480"), OptionsTestFixtures.EXPIRY, OptionsTestFixtures.START,
                new BigDecimal("0.22")).greeks().orElseThrow();

        assertThat(call.delta().subtract(put.delta()).doubleValue()).isCloseTo(1.0,
                within(1.0e-6));
        assertThat(put.delta()).isNegative();
    }

    @Test
    void deepInTheMoneyCallApproachesFullDelta() {
        Greeks greeks = engine.evaluate(OptionType.CALL, new BigDecimal("500"),
                new BigDecimal("100"), OptionsTestFixtures.EXPIRY, OptionsTestFixtures.START,
                new BigDecimal("0.20")).greeks().orElseThrow();

        assertThat(greeks.delta().doubleValue()).isGreaterThan(0.99);
    }

    @Test
    void deepOutOfTheMoneyCallHasAlmostNoDelta() {
        Greeks greeks = engine.evaluate(OptionType.CALL, new BigDecimal("500"),
                new BigDecimal("2000"), OptionsTestFixtures.EXPIRY, OptionsTestFixtures.START,
                new BigDecimal("0.20")).greeks().orElseThrow();

        assertThat(greeks.delta().doubleValue()).isLessThan(0.01);
    }

    @Test
    void higherVolatilityIncreasesVegaButNotDeltaRange() {
        Greeks low = engine.evaluate(OptionType.CALL, new BigDecimal("500"),
                new BigDecimal("500"), OptionsTestFixtures.EXPIRY, OptionsTestFixtures.START,
                new BigDecimal("0.15")).greeks().orElseThrow();
        Greeks high = engine.evaluate(OptionType.CALL, new BigDecimal("500"),
                new BigDecimal("500"), OptionsTestFixtures.EXPIRY, OptionsTestFixtures.START,
                new BigDecimal("0.45")).greeks().orElseThrow();

        assertThat(high.vega().compareTo(low.vega())).isPositive();
        assertThat(high.delta()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
    }

    @Test
    void reportsExpiredContractsAsUnavailable() {
        GreeksAssessment assessment = engine.evaluate(OptionType.CALL, new BigDecimal("500"),
                new BigDecimal("500"), OptionsTestFixtures.expiryInDays(-1),
                OptionsTestFixtures.START, new BigDecimal("0.25"));

        assertThat(assessment.status()).isEqualTo(AssessmentStatus.UNKNOWN);
        assertThat(assessment.greeks()).isEmpty();
    }

    @Test
    void rejectsVolatilityOutsideTheConfiguredRange() {
        assertThatThrownBy(() -> engine.evaluate(OptionType.CALL, new BigDecimal("500"),
                new BigDecimal("500"), OptionsTestFixtures.EXPIRY, OptionsTestFixtures.START,
                BigDecimal.ZERO))
                .isInstanceOf(OptionsValidationException.class)
                .hasMessageContaining("must be positive");
        assertThatThrownBy(() -> engine.evaluate(OptionType.CALL, new BigDecimal("500"),
                new BigDecimal("500"), OptionsTestFixtures.EXPIRY, OptionsTestFixtures.START,
                new BigDecimal("9")))
                .isInstanceOf(OptionsValidationException.class)
                .hasMessageContaining("outside the configured range");
    }

    @Test
    void isDeterministicForIdenticalInputs() {
        Greeks first = engine.evaluate(OptionType.PUT, new BigDecimal("500"),
                new BigDecimal("510"), OptionsTestFixtures.EXPIRY, OptionsTestFixtures.START,
                new BigDecimal("0.30")).greeks().orElseThrow();
        Greeks second = engine.evaluate(OptionType.PUT, new BigDecimal("500"),
                new BigDecimal("510"), OptionsTestFixtures.EXPIRY, OptionsTestFixtures.START,
                new BigDecimal("0.30")).greeks().orElseThrow();

        assertThat(first).isEqualTo(second);
    }
}
