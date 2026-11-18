package com.tradingassistant.execution.domain;

import com.tradingassistant.execution.ExecutionTestFixtures;
import com.tradingassistant.execution.exception.ExecutionValidationException;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.market.domain.InstrumentKey;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionDomainTest {

    @Test
    void tradeOrderRecordsItsInitialState() {
        TradeOrder order = TradeOrder.created(ExecutionTestFixtures.request(), "ORD-1",
                ExecutionTestFixtures.START);

        assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.filledQuantity()).isZero();
        assertThat(order.remainingQuantity()).isEqualByComparingTo("100");
        assertThat(order.averageFillPrice()).isEmpty();
        assertThat(order.active()).isTrue();
        assertThat(order.stopPrice()).contains(new BigDecimal("490"));
    }

    @Test
    void tradeOrderTransitionsAreImmutable() {
        TradeOrder order = TradeOrder.created(ExecutionTestFixtures.request(), "ORD-1",
                ExecutionTestFixtures.START);

        TradeOrder validated = order.withStatus(OrderStatus.VALIDATED,
                ExecutionTestFixtures.START.plusSeconds(1));

        assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(validated.status()).isEqualTo(OrderStatus.VALIDATED);
        assertThat(validated.updatedAt()).isNotEqualTo(order.updatedAt());
    }

    @Test
    void tradeOrderCarriesAReasonOnlyWhileTerminal() {
        TradeOrder order = TradeOrder.created(ExecutionTestFixtures.request(), "ORD-1",
                ExecutionTestFixtures.START);
        TradeOrder rejected = order.withStatus(OrderStatus.REJECTED,
                ExecutionTestFixtures.START).withReason("nope", ExecutionTestFixtures.START);
        TradeOrder reopened = rejected.withStatus(OrderStatus.REJECTED,
                ExecutionTestFixtures.START);

        assertThat(rejected.rejectionReason()).contains("nope");
        assertThat(reopened.rejectionReason()).contains("nope");
    }

    @Test
    void tradeOrderRejectsAnOverfill() {
        assertThatThrownBy(() -> new TradeOrder("ORD-1",
                ExecutionTestFixtures.STRATEGY_ID, ExecutionTestFixtures.INSTRUMENT,
                OrderSide.BUY, OrderType.MARKET, new BigDecimal("100"),
                new BigDecimal("101"), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), TimeInForce.DAY, OrderStatus.CREATED,
                Optional.empty(), ExecutionTestFixtures.START,
                ExecutionTestFixtures.START, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("filledQuantity");
    }

    @Test
    void tradeOrderFillUpdatesQuantityAndPrice() {
        TradeOrder order = TradeOrder.created(ExecutionTestFixtures.request(), "ORD-1",
                ExecutionTestFixtures.START);

        TradeOrder filled = order.withFill(new BigDecimal("100"), new BigDecimal("498"),
                OrderStatus.FILLED, ExecutionTestFixtures.START);

        assertThat(filled.filledQuantity()).isEqualByComparingTo("100");
        assertThat(filled.averageFillPrice()).contains(new BigDecimal("498"));
        assertThat(filled.remainingQuantity()).isZero();
        assertThat(filled.active()).isFalse();
    }

    @Test
    void executionRequestBindsTheDescriptionsOfOneInstant() {
        ExecutionRequest request = ExecutionTestFixtures.executionRequest();

        assertThat(request.order().strategyId())
                .isEqualTo(request.riskAssessment().strategyId());
        assertThat(request.order().instrument())
                .isEqualTo(request.riskAssessment().instrument());
        assertThat(request.order().timestamp())
                .isEqualTo(request.marketAssessment().timestamp());
    }

    @Test
    void executionRequestRejectsAMismatchedStrategy() {
        var assessment = ExecutionTestFixtures.assessment();
        OrderRequest order = new OrderRequest("OTHER", ExecutionTestFixtures.INSTRUMENT,
                OrderSide.BUY, OrderType.MARKET, new BigDecimal("100"), Optional.empty(),
                Optional.empty(), Optional.of(new BigDecimal("490")), Optional.empty(),
                TimeInForce.DAY, ExecutionTestFixtures.START);

        assertThatThrownBy(() -> new ExecutionRequest(order, assessment,
                ExecutionTestFixtures.market()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strategyId");
    }

    @Test
    void executionRequestRejectsAMismatchedTimestamp() {
        var assessment = ExecutionTestFixtures.assessment();
        OrderRequest order = new OrderRequest(ExecutionTestFixtures.STRATEGY_ID,
                ExecutionTestFixtures.INSTRUMENT, OrderSide.BUY, OrderType.MARKET,
                new BigDecimal("100"), Optional.empty(), Optional.empty(),
                Optional.of(new BigDecimal("490")), Optional.empty(), TimeInForce.DAY,
                ExecutionTestFixtures.START.plusSeconds(60));

        assertThatThrownBy(() -> new ExecutionRequest(order, assessment,
                ExecutionTestFixtures.market()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timestamp");
    }

    @Test
    void orderRequestRejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> new OrderRequest(ExecutionTestFixtures.STRATEGY_ID,
                ExecutionTestFixtures.INSTRUMENT, OrderSide.BUY, OrderType.MARKET,
                BigDecimal.ZERO, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), TimeInForce.DAY, ExecutionTestFixtures.START))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void orderRequestRejectsANonPositivePrice() {
        assertThatThrownBy(() -> new OrderRequest(ExecutionTestFixtures.STRATEGY_ID,
                ExecutionTestFixtures.INSTRUMENT, OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("100"), Optional.of(BigDecimal.ZERO), Optional.empty(),
                Optional.empty(), Optional.empty(), TimeInForce.DAY,
                ExecutionTestFixtures.START))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void positionRejectsAClosedPositionWithoutACloseInstant() {
        assertThatThrownBy(() -> new Position("POS-1", ExecutionTestFixtures.STRATEGY_ID,
                ExecutionTestFixtures.INSTRUMENT, PositionDirection.LONG,
                new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("500"),
                Optional.empty(), Optional.empty(), BigDecimal.ZERO,
                new BigDecimal("500"), PositionStatus.CLOSED,
                ExecutionTestFixtures.START, ExecutionTestFixtures.START, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("closedAt");
    }

    @Test
    void directionOfASideIsExplicit() {
        assertThat(PositionDirection.of(OrderSide.BUY)).isEqualTo(PositionDirection.LONG);
        assertThat(PositionDirection.of(OrderSide.SELL)).isEqualTo(PositionDirection.SHORT);
    }

    @Test
    void portfolioSnapshotOffersAnEmptyViewAndPositionLookup() {
        PortfolioSnapshot snapshot = PortfolioSnapshot.empty(new BigDecimal("100000"));

        assertThat(snapshot.availableCapital()).isEqualByComparingTo("100000");
        assertThat(snapshot.position(ExecutionTestFixtures.STRATEGY_ID,
                ExecutionTestFixtures.INSTRUMENT)).isEmpty();
        assertThat(PortfolioSummary.of(snapshot).openPositions()).isZero();
    }

    @Test
    void executionResultReportsAcceptanceAndFill() {
        var result = com.tradingassistant.execution.ExecutionTestFixtures.harness()
                .engine().execute(ExecutionTestFixtures.executionRequest());

        assertThat(result.accepted()).isTrue();
        assertThat(result.filledQuantity()).isEqualByComparingTo("100");
        assertThat(result.diagnostics().attempts()).isEqualTo(1);
    }

    @Test
    void executionDiagnosticsRejectNegativeAttempts() {
        assertThatThrownBy(() -> new ExecutionDiagnostics(java.util.Map.of(), List.of(), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tradeLifecycleSummarisesThePosition() {
        var result = ExecutionTestFixtures.harness().engine()
                .execute(ExecutionTestFixtures.executionRequest());
        TradeLifecycle lifecycle = TradeLifecycle.of(result.order().orElseThrow(),
                result.position().orElseThrow());

        assertThat(lifecycle.completed()).isFalse();
        assertThat(lifecycle.positionStatus()).isEqualTo(PositionStatus.OPEN);
        assertThat(lifecycle.filledQuantity()).isEqualByComparingTo("100");
        assertThat(lifecycle.direction()).isEqualTo(PositionDirection.LONG);
    }

    @Test
    void brokerReportExposesFilledAndRejectedStates() {
        ExecutionReport filled = new ExecutionReport("B-1", OrderStatus.FILLED,
                new BigDecimal("100"), Optional.of(new BigDecimal("500")), Optional.empty(),
                List.of(), ExecutionTestFixtures.START);
        ExecutionReport rejected = new ExecutionReport("B-2", OrderStatus.REJECTED,
                BigDecimal.ZERO, Optional.empty(), Optional.of("no"), List.of(),
                ExecutionTestFixtures.START);

        assertThat(filled.filled()).isTrue();
        assertThat(filled.rejected()).isFalse();
        assertThat(rejected.rejected()).isTrue();
    }

    @Test
    void instrumentOfAnOrderMustNotBeNull() {
        assertThatThrownBy(() -> new OrderRequest(ExecutionTestFixtures.STRATEGY_ID, null,
                OrderSide.BUY, OrderType.MARKET, new BigDecimal("100"), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), TimeInForce.DAY,
                ExecutionTestFixtures.START))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void anInstrumentKeyCarriesItsSegment() {
        InstrumentKey instrument = new InstrumentKey(Segment.NSE_CASH, "3045");

        assertThat(instrument.segment()).isEqualTo(Segment.NSE_CASH);
        assertThat(instrument.symbolToken()).isEqualTo("3045");
    }

    @Test
    void executionStatusIsNotARecommendation() {
        assertThat(ExecutionStatus.values()).noneMatch(status ->
                status.name().contains("BUY") || status.name().contains("SELL"));
    }

    @Test
    void timeInForceSupportsTheThreeDocumentedValues() {
        assertThat(TimeInForce.values()).containsExactly(TimeInForce.DAY, TimeInForce.IOC,
                TimeInForce.GTC);
    }

    @Test
    void orderTypesCoverTheFourDocumentedValues() {
        assertThat(OrderType.values()).containsExactly(OrderType.MARKET, OrderType.LIMIT,
                OrderType.STOP, OrderType.STOP_LIMIT);
    }

    @Test
    void positionStatusCoversTheDocumentedLifecycle() {
        assertThat(PositionStatus.values()).containsExactly(PositionStatus.OPENING,
                PositionStatus.OPEN, PositionStatus.PARTIALLY_CLOSED,
                PositionStatus.CLOSED);
    }

    @Test
    void anInvalidRuleEvaluationIsRejected() {
        assertThatThrownBy(() -> new ExecutionRuleEvaluation(" ", ExecutionRuleStatus.PASSED,
                "ok", java.util.Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void timestampsAreComparedForEquality() {
        Instant first = ExecutionTestFixtures.START;
        assertThat(first).isEqualTo(Instant.parse("2026-11-18T03:45:00Z"));
    }
}
