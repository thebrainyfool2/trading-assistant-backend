package com.tradingassistant.execution.service;

import com.tradingassistant.execution.ExecutionTestFixtures;
import com.tradingassistant.execution.domain.ExecutionResult;
import com.tradingassistant.execution.domain.ExecutionStatus;
import com.tradingassistant.execution.exception.ExecutionValidationException;
import com.tradingassistant.pipeline.event.MarketAssessmentGeneratedEvent;
import com.tradingassistant.pipeline.event.OrderCreatedEvent;
import com.tradingassistant.pipeline.event.OptionsAssessmentGeneratedEvent;
import com.tradingassistant.pipeline.event.RiskAssessmentGeneratedEvent;
import com.tradingassistant.pipeline.event.StrategyEvaluatedEvent;
import com.tradingassistant.pipeline.event.TradeQualifiedEvent;
import com.tradingassistant.qualification.domain.QualificationStatus;
import com.tradingassistant.risk.RiskTestFixtures;
import com.tradingassistant.strategy.domain.EvaluationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionServiceTest {

    private ExecutionTestFixtures.Harness harness;
    private ExecutionService service;

    @BeforeEach
    void setUp() {
        harness = ExecutionTestFixtures.harness();
        service = new ExecutionService(harness.engine(),
                (instrument, interval) -> Optional.empty(), ExecutionTestFixtures.clock());
    }

    private void publishAssessments() {
        service.onEvent(new RiskAssessmentGeneratedEvent(
                List.of(ExecutionTestFixtures.assessment()), ExecutionTestFixtures.START));
        service.onEvent(new MarketAssessmentGeneratedEvent(
                ExecutionTestFixtures.market(), ExecutionTestFixtures.START));
    }

    @Test
    void cachesRiskAssessmentsFromThePipeline() {
        service.onEvent(new RiskAssessmentGeneratedEvent(
                List.of(ExecutionTestFixtures.assessment()), ExecutionTestFixtures.START));

        assertThat(service.cachedRiskAssessmentCount()).isEqualTo(1);
        assertThat(service.latestRisk(ExecutionTestFixtures.STRATEGY_ID,
                ExecutionTestFixtures.INSTRUMENT)).isPresent();
        assertThat(service.latestRisk("other", ExecutionTestFixtures.INSTRUMENT)).isEmpty();
    }

    @Test
    void submitsAnOrderForTheCachedState() {
        publishAssessments();

        ExecutionResult result = service.submit(ExecutionTestFixtures.marketIntent());

        assertThat(result.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(result.position()).isPresent();
        assertThat(service.portfolio().openPositionCount()).isEqualTo(1);
    }

    @Test
    void refusesToSubmitWithoutARiskAssessment() {
        assertThatThrownBy(() -> service.submit(ExecutionTestFixtures.marketIntent()))
                .isInstanceOf(ExecutionValidationException.class)
                .hasMessageContaining("no risk assessment");
    }

    @Test
    void refusesToSubmitWithoutAMarketAssessment() {
        service.onEvent(new RiskAssessmentGeneratedEvent(
                List.of(ExecutionTestFixtures.assessment()), ExecutionTestFixtures.START));

        assertThatThrownBy(() -> service.submit(ExecutionTestFixtures.marketIntent()))
                .isInstanceOf(ExecutionValidationException.class)
                .hasMessageContaining("no market assessment");
    }

    @Test
    void refusesToSubmitWhenTheCachedQualificationFailed() {
        publishAssessments();
        service.onEvent(new TradeQualifiedEvent(
                List.of(RiskTestFixtures.qualification(
                        QualificationStatus.DISQUALIFIED)),
                ExecutionTestFixtures.START));

        assertThatThrownBy(() -> service.submit(ExecutionTestFixtures.marketIntent()))
                .isInstanceOf(ExecutionValidationException.class)
                .hasMessageContaining("DISQUALIFIED");
    }

    @Test
    void acceptsAQualifiedTrade() {
        publishAssessments();
        service.onEvent(new TradeQualifiedEvent(
                List.of(RiskTestFixtures.qualification(QualificationStatus.QUALIFIED)),
                ExecutionTestFixtures.START));

        assertThat(service.cachedQualificationCount()).isEqualTo(1);
        assertThat(service.submit(ExecutionTestFixtures.marketIntent()).accepted())
                .isTrue();
    }

    @Test
    void cachesOptionsAssessmentsForDiagnostics() {
        service.onEvent(new OptionsAssessmentGeneratedEvent(
                RiskTestFixtures.optionsAssessment(), ExecutionTestFixtures.START));

        assertThat(service.optionsAssessment(ExecutionTestFixtures.INSTRUMENT))
                .isPresent();
        assertThat(service.cachedOptionsAssessmentCount()).isEqualTo(1);
    }

    @Test
    void cachesStrategyEvaluationsForDiagnostics() {
        service.onEvent(new StrategyEvaluatedEvent(
                List.of(RiskTestFixtures.strategyEvaluation()), ExecutionTestFixtures.START));

        assertThat(service.strategyStatus(ExecutionTestFixtures.STRATEGY_ID,
                ExecutionTestFixtures.INSTRUMENT, ExecutionTestFixtures.START))
                .contains(EvaluationStatus.PASSED);
    }

    @Test
    void marksOpenPositionsFromTheMarketDataProvider() {
        ExecutionService marking = new ExecutionService(harness.engine(),
                (instrument, interval) -> Optional.of(new BigDecimal("510")),
                ExecutionTestFixtures.clock());
        marking.onEvent(new RiskAssessmentGeneratedEvent(
                List.of(ExecutionTestFixtures.assessment()), ExecutionTestFixtures.START));
        marking.onEvent(new MarketAssessmentGeneratedEvent(
                ExecutionTestFixtures.market(), ExecutionTestFixtures.START));
        marking.submit(ExecutionTestFixtures.marketIntent());

        marking.onEvent(new MarketAssessmentGeneratedEvent(
                ExecutionTestFixtures.market(), ExecutionTestFixtures.START));

        assertThat(harness.portfolio().snapshot().unrealisedPnl())
                .isEqualByComparingTo("1000");
    }

    @Test
    void ignoresNullAndUnrelatedEvents() {
        service.onEvent(null);
        service.onEvent(new OrderCreatedEvent(
                com.tradingassistant.execution.domain.TradeOrder.created(
                        ExecutionTestFixtures.request(), "ORD-1",
                        ExecutionTestFixtures.START), ExecutionTestFixtures.START));

        assertThat(service.cachedRiskAssessmentCount()).isZero();
        assertThat(service.cachedMarketAssessmentCount()).isZero();
    }

    @Test
    void executesARequestDirectly() {
        ExecutionResult result = service.execute(ExecutionTestFixtures.executionRequest());

        assertThat(result.status()).isEqualTo(ExecutionStatus.COMPLETED);
    }

    @Test
    void cancelDelegatesToTheEngine() {
        assertThatThrownBy(() -> service.cancel("ORD-404"))
                .isInstanceOf(ExecutionValidationException.class)
                .hasMessageContaining("unknown order");
    }

    @Test
    void exposesItsCollaborators() {
        assertThat(service.engine()).isSameAs(harness.engine());
        assertThat(service.portfolio()).isSameAs(harness.portfolio());
        assertThat(service.clock()).isEqualTo(ExecutionTestFixtures.clock());
        assertThat(service.cacheCapacity()).isEqualTo(ExecutionService.CACHE_CAPACITY);
        assertThat(service.toString()).isEqualTo("ExecutionService");
    }

    @Test
    void cachesStayBoundedByTheirCapacity() {
        ExecutionService bounded = new ExecutionService(harness.engine(),
                ExecutionMarketDataProvider.empty(), harness.portfolio(), 1,
                ExecutionTestFixtures.clock());
        bounded.onEvent(new RiskAssessmentGeneratedEvent(
                List.of(ExecutionTestFixtures.assessment()), ExecutionTestFixtures.START));
        bounded.onEvent(new MarketAssessmentGeneratedEvent(
                ExecutionTestFixtures.market(), ExecutionTestFixtures.START));
        bounded.onEvent(new OptionsAssessmentGeneratedEvent(
                RiskTestFixtures.optionsAssessment(), ExecutionTestFixtures.START));

        assertThat(bounded.cachedRiskAssessmentCount()).isLessThanOrEqualTo(1);
        assertThat(bounded.cachedMarketAssessmentCount()).isLessThanOrEqualTo(1);
        assertThat(bounded.cachedOptionsAssessmentCount()).isLessThanOrEqualTo(1);
    }
}
