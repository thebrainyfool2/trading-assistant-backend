package com.tradingassistant.execution.order;

import com.tradingassistant.execution.domain.OrderStatus;
import com.tradingassistant.execution.domain.TradeOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Bounded, thread-safe memory of recent orders.
 *
 * <p>The store is not persistence and not an audit trail: it holds the most recent
 * orders so duplicate detection and cancellation can find them, and it evicts the
 * oldest entry when the configured capacity is reached.</p>
 */
public final class OrderStore {

    private static final int DEFAULT_CAPACITY = 256;

    private final int capacity;
    private final Map<String, TradeOrder> orders;
    private final Map<String, String> brokerOrderIds = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > OrderStore.this.capacity;
                }
            });

    public OrderStore() {
        this(DEFAULT_CAPACITY);
    }

    public OrderStore(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        this.capacity = capacity;
        this.orders = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, TradeOrder> eldest) {
                return size() > OrderStore.this.capacity;
            }
        });
    }

    public void put(TradeOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }
        orders.put(order.orderId(), order);
    }

    /**
     * Records the broker identity of an order.
     */
    public void recordBrokerOrderId(String orderId, String brokerOrderId) {
        if (orderId != null && brokerOrderId != null && !brokerOrderId.isBlank()) {
            brokerOrderIds.put(orderId, brokerOrderId);
        }
    }

    public Optional<String> brokerOrderId(String orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(brokerOrderIds.get(orderId));
    }

    public Optional<TradeOrder> find(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(orders.get(orderId.trim()));
    }

    /**
     * Immutable snapshot of orders that still consume risk.
     */
    public List<TradeOrder> activeOrders() {
        List<TradeOrder> active = new ArrayList<>();
        synchronized (orders) {
            for (TradeOrder order : orders.values()) {
                if (order.status().active()) {
                    active.add(order);
                }
            }
        }
        return List.copyOf(active);
    }

    /**
     * True when an active order already exists for the same strategy, instrument
     * and side.
     */
    public boolean hasActiveOrder(String strategyId, com.tradingassistant.market.domain.InstrumentKey instrument,
                                  com.tradingassistant.execution.domain.OrderSide side) {
        return activeOrders().stream().anyMatch(order ->
                order.strategyId().equals(strategyId)
                        && order.instrument().equals(instrument)
                        && order.side() == side);
    }

    public boolean active(String orderId) {
        return find(orderId).map(order -> order.status().active()).orElse(false);
    }

    public void updateStatus(String orderId, OrderStatus status) {
        find(orderId).ifPresent(order -> put(order.withStatus(status, order.updatedAt())));
    }

    public int size() {
        return orders.size();
    }

    public int capacity() {
        return capacity;
    }

    public void clear() {
        orders.clear();
        brokerOrderIds.clear();
    }
}
