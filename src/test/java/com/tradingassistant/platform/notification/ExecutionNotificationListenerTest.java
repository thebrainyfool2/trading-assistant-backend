package com.tradingassistant.platform.notification;

import com.tradingassistant.pipeline.event.OrderCancelledEvent;
import com.tradingassistant.pipeline.event.OrderFilledEvent;
import com.tradingassistant.pipeline.event.PortfolioUpdatedEvent;
import com.tradingassistant.platform.PlatformTestFixtures;
import com.tradingassistant.platform.history.OrderHistoryService;
import com.tradingassistant.platform.preferences.UserPreferencesDocument;
import com.tradingassistant.platform.preferences.UserPreferencesService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.Clock;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionNotificationListenerTest {

    private final OrderHistoryService orderHistory = mock(OrderHistoryService.class);
    private final UserPreferencesService preferences = mock(UserPreferencesService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final ExecutionNotificationListener listener =
            new ExecutionNotificationListener(orderHistory, preferences, notifications);

    @Test
    void filledOrderNotifiesWhenPreferencesAllowIt() {
        when(orderHistory.findByEngineOrderId("ENG-1"))
                .thenReturn(Optional.of(PlatformTestFixtures.orderRecord("user-1")));
        when(preferences.getOrCreate("user-1")).thenReturn(
                UserPreferencesDocument.defaults("user-1", PlatformTestFixtures.NOW));

        listener.onEvent(new OrderFilledEvent(PlatformTestFixtures.filledOrder(),
                new com.tradingassistant.execution.domain.OrderExecution("EX-1", "ENG-1",
                        PlatformTestFixtures.amount("100"),
                        PlatformTestFixtures.amount("500"), PlatformTestFixtures.NOW),
                PlatformTestFixtures.NOW));

        verify(notifications).notify(eq("user-1"), eq(NotificationType.ORDER_UPDATE),
                eq("Order filled"), anyString());
    }

    @Test
    void accountWithoutOrderNotificationsReceivesNothing() {
        when(orderHistory.findByEngineOrderId("ENG-1"))
                .thenReturn(Optional.of(PlatformTestFixtures.orderRecord("user-1")));
        when(preferences.getOrCreate("user-1")).thenReturn(
                new UserPreferencesDocument("user-1",
                        com.tradingassistant.platform.preferences.Theme.DARK,
                        com.tradingassistant.market.domain.Interval.FIFTEEN_MINUTE, "",
                        true, false, true, true, true, PlatformTestFixtures.NOW));

        listener.onEvent(new OrderCancelledEvent(
                PlatformTestFixtures.cancelledOrder(), "cancelled",
                PlatformTestFixtures.NOW));

        verify(notifications, never()).notify(anyString(), any(), anyString(),
                anyString());
    }

    @Test
    void unknownOrderRaisesNothing() {
        when(orderHistory.findByEngineOrderId("ENG-1")).thenReturn(Optional.empty());

        listener.onEvent(new OrderFilledEvent(PlatformTestFixtures.filledOrder(),
                new com.tradingassistant.execution.domain.OrderExecution("EX-1", "ENG-1",
                        PlatformTestFixtures.amount("100"),
                        PlatformTestFixtures.amount("500"), PlatformTestFixtures.NOW),
                PlatformTestFixtures.NOW));

        verify(notifications, never()).notify(anyString(), any(), anyString(),
                anyString());
        verify(preferences, never()).getOrCreate(anyString());
    }

    @Test
    void unrelatedEventsRaiseNothing() {
        listener.onEvent(new PortfolioUpdatedEvent(PlatformTestFixtures.portfolio(),
                PlatformTestFixtures.NOW));

        verify(notifications, never()).notify(anyString(), any(), anyString(),
                anyString());
    }
}
