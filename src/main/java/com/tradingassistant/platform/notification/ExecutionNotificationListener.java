package com.tradingassistant.platform.notification;

import com.tradingassistant.platform.history.OrderRecordDocument;
import com.tradingassistant.platform.history.OrderHistoryService;
import com.tradingassistant.platform.preferences.UserPreferencesDocument;
import com.tradingassistant.platform.preferences.UserPreferencesService;
import com.tradingassistant.pipeline.event.MarketEvent;
import com.tradingassistant.pipeline.event.MarketEventListener;
import com.tradingassistant.pipeline.event.OrderCancelledEvent;
import com.tradingassistant.pipeline.event.OrderFilledEvent;
import com.tradingassistant.pipeline.event.OrderPartiallyFilledEvent;
import com.tradingassistant.pipeline.event.OrderRejectedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Raises in-app notifications for order events that arrive after submission.
 *
 * <p>The owner is resolved from the persisted order, never from the event, which
 * carries no account identity. Accounts that switched order notifications off
 * receive nothing.</p>
 */
@Component
public class ExecutionNotificationListener implements MarketEventListener {

    private static final Logger log =
            LoggerFactory.getLogger(ExecutionNotificationListener.class);

    private final OrderHistoryService orderHistory;
    private final UserPreferencesService preferences;
    private final NotificationService notifications;

    public ExecutionNotificationListener(OrderHistoryService orderHistory,
                                         UserPreferencesService preferences,
                                         NotificationService notifications) {
        this.orderHistory = orderHistory;
        this.preferences = preferences;
        this.notifications = notifications;
    }

    @Override
    public void onEvent(MarketEvent event) {
        try {
            switch (event) {
                case OrderFilledEvent filled -> notifyOwner(filled.order().orderId(),
                        "Order filled", describe(filled.order().instrument().symbolToken(),
                                "was filled"));
                case OrderPartiallyFilledEvent partial -> notifyOwner(
                        partial.order().orderId(), "Order partially filled",
                        describe(partial.order().instrument().symbolToken(),
                                "was partially filled"));
                case OrderCancelledEvent cancelled -> notifyOwner(cancelled.order().orderId(),
                        "Order cancelled", describe(cancelled.order().instrument().symbolToken(),
                                "was cancelled: " + cancelled.reason()));
                case OrderRejectedEvent rejected -> notifyOwner(rejected.order().orderId(),
                        "Order rejected", describe(rejected.order().instrument().symbolToken(),
                                "was rejected: " + rejected.reason()));
                default -> {
                    // Other events do not raise order notifications.
                }
            }
        } catch (RuntimeException failure) {
            log.warn("Notification projection failed for {}: {}", event.type(),
                    failure.toString());
        }
    }

    private void notifyOwner(String engineOrderId, String title, String message) {
        Optional<OrderRecordDocument> record = orderHistory.findByEngineOrderId(engineOrderId);
        if (record.isEmpty()) {
            return;
        }
        String userId = record.get().userId();
        UserPreferencesDocument preference = preferences.getOrCreate(userId);
        if (!preference.notificationsEnabled() || !preference.orderNotifications()) {
            return;
        }
        notifications.notify(userId, NotificationType.ORDER_UPDATE, title, message);
    }

    private static String describe(String symbolToken, String action) {
        return "Order " + symbolToken + " " + action + ".";
    }
}
