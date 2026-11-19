package com.tradingassistant.platform.executionapp;

import com.tradingassistant.common.api.ErrorCode;
import com.tradingassistant.execution.domain.ExecutionResult;
import com.tradingassistant.execution.domain.OrderIntent;
import com.tradingassistant.execution.domain.OrderSide;
import com.tradingassistant.execution.domain.OrderType;
import com.tradingassistant.execution.domain.TimeInForce;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.platform.error.PlatformException;
import com.tradingassistant.platform.history.OrderHistoryService;
import com.tradingassistant.platform.history.OrderRecordDocument;
import com.tradingassistant.platform.notification.NotificationService;
import com.tradingassistant.platform.notification.NotificationType;
import com.tradingassistant.platform.preferences.UserPreferencesDocument;
import com.tradingassistant.platform.preferences.UserPreferencesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Application use case for placing and cancelling orders.
 *
 * <p>It validates the request shape, converts it to the engine's explicit
 * {@link OrderIntent}, delegates every trading decision to the engine, and
 * records the outcome. It never sizes an order, never chooses a direction and
 * never invents a price: the caller supplies direction and the engine's risk
 * assessment supplies size and stop.</p>
 */
@Service
public class OrderApplicationService {

    private static final Logger log = LoggerFactory.getLogger(OrderApplicationService.class);

    private final ExecutionGateway gateway;
    private final OrderHistoryService history;
    private final NotificationService notifications;
    private final UserPreferencesService preferences;

    public OrderApplicationService(ExecutionGateway gateway, OrderHistoryService history,
                                   NotificationService notifications,
                                   UserPreferencesService preferences) {
        this.gateway = gateway;
        this.history = history;
        this.notifications = notifications;
        this.preferences = preferences;
    }

    /**
     * Places an order for the current assessed state.
     *
     * @throws PlatformException when execution is unavailable or a field is invalid
     */
    public OrderRecordDocument place(String userId, PlaceOrderCommand command) {
        OrderIntent intent = command.toIntent();
        ExecutionResult result = gateway.submit(intent);
        OrderRecordDocument record = history.recordSubmission(userId, intent, result);
        notifyPosition(userId, record, result);
        return record;
    }

    /**
     * Cancels an order owned by the account.
     */
    public OrderRecordDocument cancel(String userId, String orderId) {
        OrderRecordDocument record = history.requireForUser(userId, orderId);
        if (record.engineOrderId() == null || record.engineOrderId().isBlank()) {
            throw new PlatformException(ErrorCode.CONFLICT,
                    "This order was never submitted to the engine.");
        }
        if (record.status() != null && record.status().terminal()) {
            throw new PlatformException(ErrorCode.CONFLICT,
                    "The order is already in a terminal state.");
        }
        ExecutionResult result = gateway.cancel(record.engineOrderId());
        result.order().ifPresent(order -> history.apply(order, result.reason().orElse(null)));
        return history.requireForUser(userId, orderId);
    }

    private void notifyPosition(String userId, OrderRecordDocument record,
                                ExecutionResult result) {
        if (result.position().isEmpty()) {
            return;
        }
        try {
            UserPreferencesDocument preference = preferences.getOrCreate(userId);
            if (!preference.notificationsEnabled()
                    || !preference.positionNotifications()) {
                return;
            }
            notifications.notify(userId, NotificationType.POSITION_UPDATE,
                    "Position opened",
                    "Position " + result.position().get().positionId() + " on "
                            + record.symbolToken() + " was opened.");
        } catch (RuntimeException failure) {
            log.warn("Position notification could not be raised: {}", failure.toString());
        }
    }

    /**
     * Validated order request supplied by a client.
     */
    public record PlaceOrderCommand(
            String strategyId,
            Segment segment,
            String symbolToken,
            OrderSide side,
            OrderType type,
            Optional<BigDecimal> limitPrice,
            Optional<BigDecimal> triggerPrice,
            Optional<BigDecimal> targetPrice,
            TimeInForce timeInForce) {

        public PlaceOrderCommand {
            strategyId = requireText(strategyId, "strategyId");
            segment = java.util.Objects.requireNonNull(segment,
                    "segment must not be null");
            symbolToken = requireText(symbolToken, "symbolToken");
            side = java.util.Objects.requireNonNull(side, "side must not be null");
            type = java.util.Objects.requireNonNull(type, "type must not be null");
            limitPrice = positive(limitPrice, "limitPrice");
            triggerPrice = positive(triggerPrice, "triggerPrice");
            targetPrice = positive(targetPrice, "targetPrice");
            timeInForce = java.util.Objects.requireNonNull(timeInForce,
                    "timeInForce must not be null");
        }

        public OrderIntent toIntent() {
            return new OrderIntent(strategyId, new InstrumentKey(segment, symbolToken),
                    side, type, limitPrice, triggerPrice, targetPrice, timeInForce);
        }

        private static String requireText(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new PlatformException(ErrorCode.VALIDATION_FAILED,
                        field + " must not be blank.");
            }
            return value.trim();
        }

        private static Optional<BigDecimal> positive(Optional<BigDecimal> value,
                                                     String field) {
            if (value == null || value.isEmpty()) {
                return Optional.empty();
            }
            if (value.get().signum() <= 0) {
                throw new PlatformException(ErrorCode.VALIDATION_FAILED,
                        field + " must be greater than zero.");
            }
            return value;
        }
    }
}
