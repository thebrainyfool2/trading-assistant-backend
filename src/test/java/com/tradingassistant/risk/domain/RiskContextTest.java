package com.tradingassistant.risk.domain;

import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.options.OptionsTestFixtures;
import com.tradingassistant.qualification.domain.QualificationStatus;
import com.tradingassistant.risk.RiskTestFixtures;
import com.tradingassistant.risk.exception.RiskValidationException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static com.tradingassistant.risk.RiskTestFixtures.LATER;
import static com.tradingassistant.risk.RiskTestFixtures.SPOT;
import static com.tradingassistant.risk.RiskTestFixtures.START;
import static com.tradingassistant.risk.RiskTestFixtures.atrIndicator;
import static com.tradingassistant.risk.RiskTestFixtures.candle;
import static com.tradingassistant.risk.RiskTestFixtures.indicators;
import static com.tradingassistant.risk.RiskTestFixtures.marketAssessment;
import static com.tradingassistant.risk.RiskTestFixtures.optionsAssessment;
import static com.tradingassistant.risk.RiskTestFixtures.qualification;
import static com.tradingassistant.risk.RiskTestFixtures.strategyEvaluation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiskContextTest {

    @Test
    void exposesIdentityPriceAndIndicators() {
        RiskContext context = RiskTestFixtures.context();

        assertThat(context.instrument()).isEqualTo(SPOT);
        assertThat(context.interval()).isEqualTo(Interval.ONE_MINUTE);
        assertThat(context.timestamp()).isEqualTo(START);
        assertThat(context.hasOptions()).isTrue();
        assertThat(context.price()).isPresent();
        assertThat(context.atr()).isPresent();
        assertThat(context.indicatorValue("atr", "value")).isPresent();
        assertThat(context.indicatorValue("missing", "value")).isEmpty();
        assertThat(context.indicatorValue(null, "value")).isEmpty();
    }

    @Test
    void rejectsNullParts() {
        assertThatThrownBy(() -> new RiskContext(null, qualification(), marketAssessment(),
                Optional.empty(), Optional.empty(), Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("strategyEvaluation");
        assertThatThrownBy(() -> new RiskContext(strategyEvaluation(), null,
                marketAssessment(), Optional.empty(), Optional.empty(), Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("qualification");
        assertThatThrownBy(() -> new RiskContext(strategyEvaluation(), qualification(), null,
                Optional.empty(), Optional.empty(), Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("marketAssessment");
    }

    @Test
    void rejectsAQualificationOfAnotherStrategy() {
        var foreign = RiskTestFixtures.qualification("RSI_PULLBACK",
                QualificationStatus.QUALIFIED);

        assertThatThrownBy(() -> new RiskContext(strategyEvaluation(), foreign,
                marketAssessment(), Optional.empty(), Optional.empty(), Map.of()))
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("strategyId");
    }

    @Test
    void rejectsAForeignMarketInstrument() {
        assertThatThrownBy(() -> new RiskContext(strategyEvaluation(), qualification(),
                marketAssessment(OptionsTestFixtures.OTHER_SPOT), Optional.empty(),
                Optional.empty(), Map.of()))
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("instrument");
    }

    @Test
    void rejectsAMarketAssessmentOfAnotherInstant() {
        assertThatThrownBy(() -> new RiskContext(strategyEvaluation(), qualification(),
                marketAssessment(LATER), Optional.empty(), Optional.empty(), Map.of()))
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("timestamp");
    }

    @Test
    void rejectsAMarketAssessmentOfAnotherInterval() {
        assertThatThrownBy(() -> new RiskContext(strategyEvaluation(), qualification(),
                marketAssessment(START, Interval.FIVE_MINUTE), Optional.empty(),
                Optional.empty(), Map.of()))
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("interval");
    }

    @Test
    void rejectsAForeignOrStaleOptionAssessment() {
        assertThatThrownBy(() -> new RiskContext(strategyEvaluation(), qualification(),
                marketAssessment(), Optional.of(optionsAssessment(
                        OptionsTestFixtures.OTHER_SPOT, START)), Optional.empty(), Map.of()))
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("options assessment");
        assertThatThrownBy(() -> new RiskContext(strategyEvaluation(), qualification(),
                marketAssessment(), Optional.of(optionsAssessment(LATER)), Optional.empty(),
                Map.of()))
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("timestamp");
    }

    @Test
    void rejectsACandleOfAnotherInstantInstrumentOrInterval() {
        assertThatThrownBy(() -> new RiskContext(strategyEvaluation(), qualification(),
                marketAssessment(), Optional.empty(), Optional.of(candle(LATER)), Map.of()))
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("candle");
        assertThatThrownBy(() -> new RiskContext(strategyEvaluation(), qualification(),
                marketAssessment(), Optional.empty(),
                Optional.of(candle(OptionsTestFixtures.OTHER_SPOT, START,
                        Interval.ONE_MINUTE)), Map.of()))
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("candle");
        assertThatThrownBy(() -> new RiskContext(strategyEvaluation(), qualification(),
                marketAssessment(), Optional.empty(),
                Optional.of(candle(SPOT, START, Interval.FIVE_MINUTE)), Map.of()))
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("candle");
    }

    @Test
    void rejectsANegativeAtr() {
        assertThatThrownBy(() -> new RiskContext(strategyEvaluation(), qualification(),
                marketAssessment(), Optional.empty(), Optional.empty(),
                indicators(atrIndicator("-1"))))
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("ATR");
    }

    @Test
    void rejectsBlankIndicatorKeys() {
        assertThatThrownBy(() -> new RiskContext(strategyEvaluation(), qualification(),
                marketAssessment(), Optional.empty(), Optional.empty(),
                Map.of(" ", atrIndicator("5"))))
                .isInstanceOf(RiskValidationException.class)
                .hasMessageContaining("indicators");
    }

    @Test
    void fallsBackToTheOptionSpotWithoutACandle() {
        RiskContext context = new RiskContext(strategyEvaluation(), qualification(),
                marketAssessment(), Optional.of(optionsAssessment()), Optional.empty(),
                Map.of());

        assertThat(context.price()).isPresent();
        assertThat(context.price().orElseThrow()).isEqualByComparingTo(
                RiskTestFixtures.PRICE);
    }
}
