package com.tradingassistant.execution.order;

import com.tradingassistant.execution.domain.OrderStatus;
import com.tradingassistant.execution.exception.ExecutionValidationException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Legal order transitions.
 *
 * <p>An order moves forward only. A terminal state can never be reopened, and a
 * fill can never be recorded on an order that was rejected or cancelled. Keeping
 * the table explicit means a broker report cannot silently rewrite history.</p>
 */
public final class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = transitions();

    private OrderStateMachine() {
    }

    public static boolean canTransition(OrderStatus from, OrderStatus to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to && from == OrderStatus.PARTIALLY_FILLED) {
            return true;
        }
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /**
     * Rejects an illegal transition, naming both states.
     */
    public static void requireTransition(OrderStatus from, OrderStatus to) {
        if (!canTransition(from, to)) {
            throw new ExecutionValidationException(
                    "illegal order transition from " + from + " to " + to);
        }
    }

    private static Map<OrderStatus, Set<OrderStatus>> transitions() {
        Map<OrderStatus, Set<OrderStatus>> allowed = new EnumMap<>(OrderStatus.class);
        allowed.put(OrderStatus.CREATED, EnumSet.of(OrderStatus.VALIDATED,
                OrderStatus.REJECTED, OrderStatus.FAILED));
        allowed.put(OrderStatus.VALIDATED, EnumSet.of(OrderStatus.SUBMITTED,
                OrderStatus.REJECTED, OrderStatus.FAILED));
        allowed.put(OrderStatus.SUBMITTED, EnumSet.of(OrderStatus.PENDING,
                OrderStatus.PARTIALLY_FILLED, OrderStatus.FILLED, OrderStatus.REJECTED,
                OrderStatus.FAILED, OrderStatus.CANCELLED, OrderStatus.EXPIRED));
        allowed.put(OrderStatus.PENDING, EnumSet.of(OrderStatus.PARTIALLY_FILLED,
                OrderStatus.FILLED, OrderStatus.CANCELLED, OrderStatus.REJECTED,
                OrderStatus.EXPIRED));
        allowed.put(OrderStatus.PARTIALLY_FILLED, EnumSet.of(OrderStatus.FILLED,
                OrderStatus.CANCELLED, OrderStatus.EXPIRED));
        allowed.put(OrderStatus.FILLED, EnumSet.noneOf(OrderStatus.class));
        allowed.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
        allowed.put(OrderStatus.REJECTED, EnumSet.noneOf(OrderStatus.class));
        allowed.put(OrderStatus.FAILED, EnumSet.noneOf(OrderStatus.class));
        allowed.put(OrderStatus.EXPIRED, EnumSet.noneOf(OrderStatus.class));
        return allowed;
    }
}
