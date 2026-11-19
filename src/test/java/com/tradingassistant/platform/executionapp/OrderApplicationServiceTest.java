package com.tradingassistant.platform.executionapp;

import com.tradingassistant.common.api.ErrorCode;
import com.tradingassistant.execution.domain.ExecutionResult;
import com.tradingassistant.execution.domain.ExecutionStatus;
import com.tradingassistant.execution.domain.OrderStatus;
import com.tradingassistant.platform.PlatformTestFixtures;
import com.tradingassistant.platform.error.PlatformException;
import com.tradingassistant.platform.history.OrderHistoryService;
import com.tradingassistant.platform.history.OrderRecordDocument;
import com.tradingassistant.platform.notification.NotificationService;
import com.tradingassistant.platform.notification.NotificationType;
import com.tradingassistant.platform.preferences.UserPreferencesDocument;
import com.tradingassistant.platform.preferences.UserPreferencesService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderApplicationServiceTest {

    private final ExecutionGateway gateway = mock(ExecutionGateway.class);
    private final OrderHistoryService history = mock(OrderHistoryService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final UserPreferencesService preferences = mock(UserPreferencesService.class);
    private final OrderApplicationService service = new OrderApplicationService(gateway,
            history, notifications, preferences);

    @Test
    void placementDelegatesToTheEngineAndRecordsTheOutcome() {
        ExecutionResult result = PlatformTestFixtures.completedResult();
        OrderRecordDocument record = PlatformTestFixtures.orderRecord("user-1");
        when(gateway.submit(any())).thenReturn(result);
        when(history.recordSubmission(eq("user-1"), any(), eq(result)))
                .thenReturn(record);
        when(preferences.getOrCreate("user-1")).thenReturn(
                UserPreferencesDocument.defaults("user-1", PlatformTestFixtures.NOW));

        OrderRecordDocument returned = service.place("user-1",
                PlatformTestFixtures.command());

        assertThat(returned).isSameAs(record);
        verify(gateway).submit(any());
        verify(notifications).notify(eq("user-1"), eq(NotificationType.POSITION_UPDATE),
                eq("Position opened"), any(String.class));
    }

    @Test
    void rejectedPlacementStillProducesHistoryWithoutPositionNotification() {
        ExecutionResult result = PlatformTestFixtures.rejectedResult();
        OrderRecordDocument record = OrderRecordDocument.from("user-1", "EMA_TREND",
                com.tradingassistant.market.domain.Segment.NSE_CASH, "2885",
                com.tradingassistant.execution.domain.OrderSide.BUY,
                com.tradingassistant.execution.domain.OrderType.MARKET,
                PlatformTestFixtures.amount("0"), null, null, null,
                com.tradingassistant.execution.domain.TimeInForce.DAY,
                ExecutionStatus.REJECTED, null, "rejected", PlatformTestFixtures.NOW);
        when(gateway.submit(any())).thenReturn(result);
        when(history.recordSubmission(eq("user-1"), any(), eq(result)))
                .thenReturn(record);

        service.place("user-1", PlatformTestFixtures.command());

        verify(preferences, never()).getOrCreate(any());
        verify(notifications, never()).notify(any(), any(), any(), any());
    }

    @Test
    void positionNotificationIsSuppressedWhenTheAccountDisabledIt() {
        ExecutionResult result = PlatformTestFixtures.completedResult();
        when(gateway.submit(any())).thenReturn(result);
        when(history.recordSubmission(eq("user-1"), any(), eq(result)))
                .thenReturn(PlatformTestFixtures.orderRecord("user-1"));
        when(preferences.getOrCreate("user-1")).thenReturn(
                new UserPreferencesDocument("user-1",
                        com.tradingassistant.platform.preferences.Theme.DARK,
                        com.tradingassistant.market.domain.Interval.FIFTEEN_MINUTE, "",
                        true, true, false, true, true, PlatformTestFixtures.NOW));

        service.place("user-1", PlatformTestFixtures.command());

        verify(notifications, never()).notify(any(), any(), any(), any());
    }

    @Test
    void notificationFailureDoesNotFailAPlacedOrder() {
        ExecutionResult result = PlatformTestFixtures.completedResult();
        when(gateway.submit(any())).thenReturn(result);
        when(history.recordSubmission(eq("user-1"), any(), eq(result)))
                .thenReturn(PlatformTestFixtures.orderRecord("user-1"));
        when(preferences.getOrCreate("user-1"))
                .thenThrow(new IllegalStateException("database down"));

        assertThat(service.place("user-1", PlatformTestFixtures.command()))
                .isNotNull();
    }

    @Test
    void engineUnavailabilityIsPropagated() {
        when(gateway.submit(any())).thenThrow(new PlatformException(
                ErrorCode.ENGINE_UNAVAILABLE, "Execution is disabled."));

        assertThatThrownBy(() -> service.place("user-1",
                PlatformTestFixtures.command()))
                .isInstanceOf(PlatformException.class)
                .extracting(failure -> ((PlatformException) failure).errorCode())
                .isEqualTo(ErrorCode.ENGINE_UNAVAILABLE);
        verify(history, never()).recordSubmission(any(), any(), any());
    }

    @Test
    void cancelRejectsATerminalOrder() {
        when(history.requireForUser("user-1", "order-1"))
                .thenReturn(PlatformTestFixtures.orderRecord("user-1"));

        assertThatThrownBy(() -> service.cancel("user-1", "order-1"))
                .isInstanceOf(PlatformException.class)
                .extracting(failure -> ((PlatformException) failure).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
        verify(gateway, never()).cancel(any());
    }

    @Test
    void cancelRejectsAnOrderThatNeverReachedTheEngine() {
        OrderRecordDocument rejected = OrderRecordDocument.from("user-1", "EMA_TREND",
                com.tradingassistant.market.domain.Segment.NSE_CASH, "2885",
                com.tradingassistant.execution.domain.OrderSide.BUY,
                com.tradingassistant.execution.domain.OrderType.MARKET,
                PlatformTestFixtures.amount("0"), null, null, null,
                com.tradingassistant.execution.domain.TimeInForce.DAY,
                ExecutionStatus.REJECTED, null, "rejected", PlatformTestFixtures.NOW);
        when(history.requireForUser("user-1", "order-1")).thenReturn(rejected);

        assertThatThrownBy(() -> service.cancel("user-1", "order-1"))
                .isInstanceOf(PlatformException.class)
                .extracting(failure -> ((PlatformException) failure).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void cancelAppliesTheEngineResult() {
        OrderRecordDocument active = PlatformTestFixtures.orderRecord("user-1")
                .withOrder(PlatformTestFixtures.filledOrder()
                        .withStatus(OrderStatus.SUBMITTED, PlatformTestFixtures.NOW),
                        null, PlatformTestFixtures.NOW);
        OrderRecordDocument cancelled = active.withOrder(
                PlatformTestFixtures.cancelledOrder(), "cancelled",
                PlatformTestFixtures.NOW);
        when(history.requireForUser("user-1", "order-1"))
                .thenReturn(active, cancelled);
        when(gateway.cancel("ENG-1")).thenReturn(new ExecutionResult(
                ExecutionStatus.CANCELLED,
                Optional.of(PlatformTestFixtures.cancelledOrder()), Optional.empty(),
                PlatformTestFixtures.portfolio(), java.util.List.of(), Optional.empty(),
                Optional.of("cancelled"), com.tradingassistant.execution.domain
                .ExecutionDiagnostics.empty()));

        OrderRecordDocument result = service.cancel("user-1", "order-1");

        assertThat(result).isSameAs(cancelled);
        verify(history).apply(PlatformTestFixtures.cancelledOrder(), "cancelled");
    }
}
