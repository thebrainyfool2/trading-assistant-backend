package com.tradingassistant.options.engine;

import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.options.OptionsTestFixtures;
import com.tradingassistant.options.domain.ChainSnapshot;
import com.tradingassistant.options.domain.ChainStateSnapshot;
import com.tradingassistant.options.domain.OptionsAnalysisContext;
import com.tradingassistant.options.domain.OptionsAssessment;
import com.tradingassistant.options.exception.OptionsValidationException;
import com.tradingassistant.options.volatility.ImpliedVolatilityHistory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptionsAnalyticsEngineTest {

    private final OptionsAnalyticsEngine engine =
            OptionsAnalyticsEngine.standard(OptionsTestFixtures.CONFIG);

    @Test
    void producesAPartialAssessmentForTheFirstCapture() {
        ChainSnapshot snapshot = OptionsTestFixtures.defaultSnapshot();

        OptionsAssessment assessment = engine.analyze(
                OptionsAnalysisContext.of(snapshot,
                        OptionsTestFixtures.marketAssessment(OptionsTestFixtures.START)));

        assertThat(assessment.status()).isEqualTo(AssessmentStatus.PARTIAL);
        assertThat(assessment.warmup()).isTrue();
        assertThat(assessment.chain().legCount()).isEqualTo(18);
        assertThat(assessment.chain().strikeCount()).isEqualTo(9);
        assertThat(assessment.volatility().atmImpliedVolatility()).isPresent();
        assertThat(assessment.greeks().atmCall()).isPresent();
        assertThat(assessment.greeks().atmPut()).isPresent();
        assertThat(assessment.openInterest().status()).isEqualTo(AssessmentStatus.PARTIAL);
        assertThat(assessment.putCallRatio().chainOpenInterestRatio()).isPresent();
        assertThat(assessment.expectedMove().expiryMove()).isPresent();
        assertThat(assessment.liquidity().state()).isNotNull();
        assertThat(assessment.maxPain().maxPainStrike()).isPresent();
    }

    @Test
    void becomesReadyOnceChangeHistoryExists() {
        ChainSnapshot snapshot = OptionsTestFixtures.defaultSnapshot();
        ImpliedVolatilityHistory history = new ImpliedVolatilityHistory(10);
        history.record(OptionsTestFixtures.START.minusSeconds(60), new java.math.BigDecimal("0.20"));

        OptionsAssessment first = engine.analyze(
                new OptionsAnalysisContext(snapshot,
                        OptionsTestFixtures.marketAssessment(OptionsTestFixtures.START),
                        ChainStateSnapshot.empty()),
                history);
        OptionsAssessment second = engine.analyze(
                new OptionsAnalysisContext(snapshot,
                        OptionsTestFixtures.marketAssessment(OptionsTestFixtures.START),
                        ChainStateSnapshot.of(snapshot)),
                history);

        assertThat(first.openInterest().status()).isEqualTo(AssessmentStatus.PARTIAL);
        assertThat(second.openInterest().status()).isEqualTo(AssessmentStatus.READY);
        assertThat(second.status()).isEqualTo(AssessmentStatus.READY);
        assertThat(second.warmup()).isFalse();
        assertThat(second.volatility().impliedVolatilityChange()).isPresent();
    }

    @Test
    void carriesTheMarketSessionContext() {
        OptionsAssessment assessment = engine.analyze(OptionsAnalysisContext.of(
                OptionsTestFixtures.defaultSnapshot(),
                OptionsTestFixtures.marketAssessment(OptionsTestFixtures.START)));

        assertThat(assessment.session().summary()).isEqualTo("test session");
        assertThat(assessment.timestamp()).isEqualTo(OptionsTestFixtures.START);
        assertThat(assessment.underlying()).isEqualTo(OptionsTestFixtures.UNDERLYING);
    }

    @Test
    void exposesDeterministicDiagnostics() {
        OptionsAssessment assessment = engine.analyze(OptionsAnalysisContext.of(
                OptionsTestFixtures.defaultSnapshot(),
                OptionsTestFixtures.marketAssessment(OptionsTestFixtures.START)));

        assertThat(assessment.diagnostics()).containsEntry("legs", "18")
                .containsEntry("strikes", "9")
                .containsEntry("expiries", "1")
                .containsEntry("previousLegs", "0");
        assertThatThrownBy(() -> assessment.diagnostics().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsAChainCapturedAtAnotherInstant() {
        ChainSnapshot snapshot = OptionsTestFixtures.defaultSnapshot();

        assertThatThrownBy(() -> new OptionsAnalysisContext(snapshot,
                OptionsTestFixtures.marketAssessment(OptionsTestFixtures.START.plusSeconds(60)),
                ChainStateSnapshot.empty()))
                .isInstanceOf(OptionsValidationException.class)
                .hasMessageContaining("timestamp");
    }

    @Test
    void rejectsAChainAndAssessmentOfDifferentInstruments() {
        ChainSnapshot snapshot = OptionsTestFixtures.defaultSnapshot();

        assertThatThrownBy(() -> new OptionsAnalysisContext(snapshot,
                OptionsTestFixtures.marketAssessment(OptionsTestFixtures.OTHER_SPOT,
                        OptionsTestFixtures.START), ChainStateSnapshot.empty()))
                .isInstanceOf(OptionsValidationException.class)
                .hasMessageContaining("same instrument");
    }

    @Test
    void isDeterministicForIdenticalInputs() {
        ChainSnapshot snapshot = OptionsTestFixtures.defaultSnapshot();
        OptionsAnalysisContext context = OptionsAnalysisContext.of(snapshot,
                OptionsTestFixtures.marketAssessment(OptionsTestFixtures.START));

        OptionsAssessment first = engine.analyze(context);
        OptionsAssessment second = engine.analyze(context);

        assertThat(first).isEqualTo(second);
    }
}
