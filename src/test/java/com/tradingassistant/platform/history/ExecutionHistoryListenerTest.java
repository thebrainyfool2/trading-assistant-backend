package com.tradingassistant.platform.history;

import com.tradingassistant.execution.domain.TradeLifecycle;
import com.tradingassistant.pipeline.event.OrderCancelledEvent;
import com.tradingassistant.pipeline.event.OrderFilledEvent;
import com.tradingassistant.pipeline.event.PortfolioUpdatedEvent;
import com.tradingassistant.pipeline.event.TradeCompletedEvent;
import com.tradingassistant.platform.PlatformTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionHistoryListenerTest {

    private final OrderHistoryService orderHistory = mock(OrderHistoryService.class);
    private final TradeHistoryService tradeHistory = mock(TradeHistoryService.class);
    private final ExecutionHistoryListener listener = new ExecutionHistoryListener(
            orderHistory, tradeHistory);

    @Test
    void filledOrderUpdatesHistory() {
        listener.onEvent(new OrderFilledEvent(PlatformTestFixtures.filledOrder(),
                new com.tradingassistant.execution.domain.OrderExecution("EX-1", "ENG-1",
                        PlatformTestFixtures.amount("100"),
                        PlatformTestFixtures.amount("500"), PlatformTestFixtures.NOW),
                PlatformTestFixtures.NOW));

        verify(orderHistory).apply(PlatformTestFixtures.filledOrder(), null);
    }

    @Test
    void cancellationCarriesTheReason() {
        listener.onEvent(new OrderCancelledEvent(
                PlatformTestFixtures.cancelledOrder(), "cancelled by user",
                PlatformTestFixtures.NOW));

        verify(orderHistory).apply(PlatformTestFixtures.cancelledOrder(),
                "cancelled by user");
    }

    @Test
    void completedTradeIsScopedToTheOrderOwner() {
        TradeLifecycle lifecycle = TradeLifecycle.of(
                PlatformTestFixtures.filledOrder(), PlatformTestFixtures.openPosition());
        when(orderHistory.findByEngineOrderId("ENG-1"))
                .thenReturn(Optional.of(PlatformTestFixtures.orderRecord("user-1")));

        listener.onEvent(new TradeCompletedEvent(lifecycle,
                PlatformTestFixtures.NOW));

        verify(tradeHistory).record("user-1", lifecycle);
    }

    @Test
    void completedTradeWithoutAnOrderIsIgnored() {
        when(orderHistory.findByEngineOrderId("ENG-1")).thenReturn(Optional.empty());

        listener.onEvent(new TradeCompletedEvent(TradeLifecycle.of(
                PlatformTestFixtures.filledOrder(), PlatformTestFixtures.openPosition()),
                PlatformTestFixtures.NOW));

        verify(tradeHistory, never()).record(anyString(), any());
    }

    @Test
    void unrelatedEventsAreIgnored() {
        listener.onEvent(new PortfolioUpdatedEvent(PlatformTestFixtures.portfolio(),
                PlatformTestFixtures.NOW));

        verify(orderHistory, never()).apply(any(), any());
        verify(tradeHistory, never()).record(anyString(), any());
    }

    @Test
    void projectionFailureIsContained() {
        doThrow(new IllegalStateException("database down"))
                .when(orderHistory).apply(any(), any());

        listener.onEvent(new OrderFilledEvent(PlatformTestFixtures.filledOrder(),
                new com.tradingassistant.execution.domain.OrderExecution("EX-1", "ENG-1",
                        PlatformTestFixtures.amount("100"),
                        PlatformTestFixtures.amount("500"), PlatformTestFixtures.NOW),
                PlatformTestFixtures.NOW));
    }
}
