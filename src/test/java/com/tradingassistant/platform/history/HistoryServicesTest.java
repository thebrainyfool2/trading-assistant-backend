package com.tradingassistant.platform.history;

import com.tradingassistant.execution.domain.OrderStatus;
import com.tradingassistant.platform.PlatformTestFixtures;
import com.tradingassistant.platform.error.PlatformException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HistoryServicesTest {

    private final OrderRecordRepository orders = mock(OrderRecordRepository.class);
    private final TradeRecordRepository trades = mock(TradeRecordRepository.class);
    private final PortfolioSnapshotRepository snapshots =
            mock(PortfolioSnapshotRepository.class);
    private final Clock clock = Clock.fixed(PlatformTestFixtures.NOW,
            java.time.ZoneOffset.UTC);

    private final OrderHistoryService orderHistory = new OrderHistoryService(orders, clock);
    private final TradeHistoryService tradeHistory = new TradeHistoryService(trades, clock);
    private final PortfolioSnapshotService snapshotService =
            new PortfolioSnapshotService(snapshots, clock);

    @Test
    void completedSubmissionIsRecordedWithItsPosition() {
        when(orders.save(any(OrderRecordDocument.class))).thenAnswer(invocation -> {
            OrderRecordDocument document = invocation.getArgument(0);
            return new OrderRecordDocument("order-1", document.userId(),
                    document.engineOrderId(), document.strategyId(), document.segment(),
                    document.symbolToken(), document.side(), document.type(),
                    document.quantity(), document.filledQuantity(),
                    document.limitPrice(), document.triggerPrice(),
                    document.stopPrice(), document.targetPrice(),
                    document.timeInForce(), document.status(),
                    document.averageFillPrice(), document.executionStatus(),
                    document.reason(), document.positionId(), document.createdAt(),
                    document.updatedAt());
        });

        OrderRecordDocument saved = orderHistory.recordSubmission("user-1",
                PlatformTestFixtures.command().toIntent(),
                PlatformTestFixtures.completedResult());

        assertThat(saved.id()).isEqualTo("order-1");
        assertThat(saved.engineOrderId()).isEqualTo("ENG-1");
        assertThat(saved.userId()).isEqualTo("user-1");
        assertThat(saved.positionId()).isEqualTo("POS-1");
        assertThat(saved.createdAt()).isEqualTo(PlatformTestFixtures.NOW);
    }

    @Test
    void rejectedSubmissionIsRecordedWithoutAnEngineOrder() {
        when(orders.save(any(OrderRecordDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderRecordDocument saved = orderHistory.recordSubmission("user-1",
                PlatformTestFixtures.command().toIntent(),
                PlatformTestFixtures.rejectedResult());

        assertThat(saved.engineOrderId()).isNull();
        assertThat(saved.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(saved.quantity()).isZero();
        assertThat(saved.executionStatus()).isEqualTo(
                com.tradingassistant.execution.domain.ExecutionStatus.REJECTED);
    }

    @Test
    void lifecycleUpdateIsAppliedToTheRecordedOrder() {
        when(orders.findByEngineOrderId("ENG-1"))
                .thenReturn(Optional.of(PlatformTestFixtures.orderRecord("user-1")));
        when(orders.save(any(OrderRecordDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        orderHistory.apply(PlatformTestFixtures.cancelledOrder(), "cancelled");

        verify(orders).save(any(OrderRecordDocument.class));
    }

    @Test
    void lifecycleUpdateForAnUnknownOrderIsIgnored() {
        when(orders.findByEngineOrderId("ENG-1")).thenReturn(Optional.empty());

        orderHistory.apply(PlatformTestFixtures.cancelledOrder(), "cancelled");

        verify(orders, never()).save(any());
    }

    @Test
    void orderLookupIsScopedByOwner() {
        when(orders.findByIdAndUserId("order-1", "user-2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderHistory.requireForUser("user-2", "order-1"))
                .isInstanceOf(PlatformException.class)
                .extracting(failure -> ((PlatformException) failure).errorCode())
                .isEqualTo(com.tradingassistant.common.api.ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void orderPagesAreClamped() {
        when(orders.findByUserIdOrderByCreatedAtDesc(eq("user-1"), any()))
                .thenReturn(Page.empty());

        orderHistory.listForUser("user-1", 0, 5_000);

        verify(orders).findByUserIdOrderByCreatedAtDesc(eq("user-1"),
                eq(PageRequest.of(0, 100)));
    }

    @Test
    void tradeIsRecordedOncePerLifecycleUpdate() {
        when(trades.existsByTradeKey("ENG-1:" + PlatformTestFixtures.NOW))
                .thenReturn(false);
        when(trades.save(any(TradeRecordDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        tradeHistory.record("user-1", com.tradingassistant.execution.domain.TradeLifecycle.of(
                PlatformTestFixtures.filledOrder(), PlatformTestFixtures.openPosition()));

        verify(trades).save(any(TradeRecordDocument.class));
    }

    @Test
    void duplicateTradeLifecycleIsIgnored() {
        when(trades.existsByTradeKey("ENG-1:" + PlatformTestFixtures.NOW))
                .thenReturn(true);

        tradeHistory.record("user-1", com.tradingassistant.execution.domain.TradeLifecycle.of(
                PlatformTestFixtures.filledOrder(), PlatformTestFixtures.openPosition()));

        verify(trades, never()).save(any());
    }

    @Test
    void portfolioSnapshotIsCaptured() {
        when(snapshots.save(any(PortfolioSnapshotDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(snapshotService.capture(PlatformTestFixtures.portfolio()))
                .isPresent()
                .get()
                .extracting(PortfolioSnapshotDocument::portfolioId)
                .isEqualTo("engine");
    }

    @Test
    void latestSnapshotIsReadForTheEnginePortfolio() {
        PortfolioSnapshotDocument document = PlatformTestFixtures.portfolioSnapshot();
        when(snapshots.findFirstByPortfolioIdOrderByCapturedAtDesc("engine"))
                .thenReturn(Optional.of(document));

        assertThat(snapshotService.latest()).contains(document);
    }
}
