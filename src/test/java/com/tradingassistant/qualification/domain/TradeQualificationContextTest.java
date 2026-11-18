package com.tradingassistant.qualification.domain;

import com.tradingassistant.intelligence.domain.SessionPhase;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.options.OptionsTestFixtures;
import com.tradingassistant.qualification.QualificationTestFixtures;
import com.tradingassistant.qualification.exception.QualificationValidationException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.tradingassistant.qualification.QualificationTestFixtures.LATER;
import static com.tradingassistant.qualification.QualificationTestFixtures.SPOT;
import static com.tradingassistant.qualification.QualificationTestFixtures.START;
import static com.tradingassistant.qualification.QualificationTestFixtures.candle;
import static com.tradingassistant.qualification.QualificationTestFixtures.marketAssessment;
import static com.tradingassistant.qualification.QualificationTestFixtures.optionsAssessment;
import static com.tradingassistant.qualification.QualificationTestFixtures.session;
import static com.tradingassistant.qualification.QualificationTestFixtures.strategyEvaluation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradeQualificationContextTest {

    @Test
    void derivesSessionAndIdentityFromTheStrategyEvaluation() {
        TradeQualificationContext context = QualificationTestFixtures.context();

        assertThat(context.instrument()).isEqualTo(SPOT);
        assertThat(context.interval()).isEqualTo(Interval.ONE_MINUTE);
        assertThat(context.timestamp()).isEqualTo(START);
        assertThat(context.session().phase()).isEqualTo(SessionPhase.REGULAR);
        assertThat(context.hasOptions()).isFalse();
        assertThat(context.candle()).isPresent();
    }

    @Test
    void rejectsNullParts() {
        assertThatThrownBy(() -> new TradeQualificationContext(null, marketAssessment(),
                Optional.empty(), session(SessionPhase.REGULAR,
                com.tradingassistant.intelligence.domain.AssessmentStatus.READY),
                Optional.empty()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("strategyEvaluation");
        assertThatThrownBy(() -> new TradeQualificationContext(strategyEvaluation(), null,
                Optional.empty(), session(SessionPhase.REGULAR,
                com.tradingassistant.intelligence.domain.AssessmentStatus.READY),
                Optional.empty()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("marketAssessment");
        assertThatThrownBy(() -> new TradeQualificationContext(strategyEvaluation(),
                marketAssessment(), Optional.empty(), null, Optional.empty()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("session");
    }

    @Test
    void rejectsAForeignMarketInstrument() {
        assertThatThrownBy(() -> QualificationTestFixtures.context(
                marketAssessment(OptionsTestFixtures.OTHER_SPOT, START)))
                .isInstanceOf(QualificationValidationException.class)
                .hasMessageContaining("instrument");
    }

    @Test
    void rejectsAMarketAssessmentOfAnotherInstant() {
        assertThatThrownBy(() -> QualificationTestFixtures.context(
                marketAssessment(SPOT, LATER)))
                .isInstanceOf(QualificationValidationException.class)
                .hasMessageContaining("timestamp");
    }

    @Test
    void rejectsAMarketAssessmentOfAnotherInterval() {
        assertThatThrownBy(() -> QualificationTestFixtures.context(
                marketAssessment(SPOT, START, Interval.FIVE_MINUTE)))
                .isInstanceOf(QualificationValidationException.class)
                .hasMessageContaining("interval");
    }

    @Test
    void rejectsASessionPhaseThatContradictsTheMarketAssessment() {
        assertThatThrownBy(() -> new TradeQualificationContext(strategyEvaluation(),
                marketAssessment(), Optional.empty(),
                session(SessionPhase.CLOSING,
                        com.tradingassistant.intelligence.domain.AssessmentStatus.READY),
                Optional.empty()))
                .isInstanceOf(QualificationValidationException.class)
                .hasMessageContaining("session");
    }

    @Test
    void rejectsAForeignOptionInstrument() {
        assertThatThrownBy(() -> QualificationTestFixtures.context(
                marketAssessment(), optionsAssessment(OptionsTestFixtures.OTHER_SPOT, START)))
                .isInstanceOf(QualificationValidationException.class)
                .hasMessageContaining("options assessment");
    }

    @Test
    void rejectsACandleOfAnotherInstantInstrumentOrInterval() {
        assertThatThrownBy(() -> QualificationTestFixtures.context(strategyEvaluation(),
                marketAssessment(), null, candle(LATER)))
                .isInstanceOf(QualificationValidationException.class)
                .hasMessageContaining("candle");
        assertThatThrownBy(() -> QualificationTestFixtures.context(strategyEvaluation(),
                marketAssessment(), null,
                candle(OptionsTestFixtures.OTHER_SPOT, START, Interval.ONE_MINUTE)))
                .isInstanceOf(QualificationValidationException.class)
                .hasMessageContaining("candle");
        assertThatThrownBy(() -> QualificationTestFixtures.context(strategyEvaluation(),
                marketAssessment(), null, candle(SPOT, START, Interval.FIVE_MINUTE)))
                .isInstanceOf(QualificationValidationException.class)
                .hasMessageContaining("candle");
    }

    @Test
    void acceptsAnOptionAssessmentOfAnotherInstantSoARuleCanRejectIt() {
        TradeQualificationContext context = QualificationTestFixtures.context(
                marketAssessment(), optionsAssessment(LATER));

        assertThat(context.hasOptions()).isTrue();
        assertThat(context.optionsAssessment().orElseThrow().timestamp()).isEqualTo(LATER);
    }
}
