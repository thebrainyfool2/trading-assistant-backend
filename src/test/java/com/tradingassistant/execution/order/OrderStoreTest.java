package com.tradingassistant.execution.order;

import com.tradingassistant.execution.ExecutionTestFixtures;
import com.tradingassistant.execution.domain.OrderSide;
import com.tradingassistant.execution.domain.OrderStatus;
import com.tradingassistant.execution.domain.OrderType;
import com.tradingassistant.execution.domain.TimeInForce;
import com.tradingassistant.execution.domain.TradeOrder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStoreTest {

    private static TradeOrder order(String id, OrderStatus status) {
        return new TradeOrder(id, ExecutionTestFixtures.STRATEGY_ID,
                ExecutionTestFixtures.INSTRUMENT, OrderSide.BUY, OrderType.MARKET,
                new BigDecimal("100"), BigDecimal.ZERO, Optional.empty(), Optional.empty(),
                Optional.of(new BigDecimal("490")), Optional.empty(), TimeInForce.DAY,
                status, Optional.empty(), ExecutionTestFixtures.START,
                ExecutionTestFixtures.START, Optional.empty());
    }

    @Test
    void storesAndFindsOrders() {
        OrderStore store = new OrderStore(4);
        store.put(order("ORD-1", OrderStatus.CREATED));

        assertThat(store.find("ORD-1")).isPresent();
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void unknownAndBlankIdsYieldEmpty() {
        OrderStore store = new OrderStore();

        assertThat(store.find("missing")).isEmpty();
        assertThat(store.find(" ")).isEmpty();
        assertThat(store.find(null)).isEmpty();
    }

    @Test
    void evictsTheOldestOrderAtCapacity() {
        OrderStore store = new OrderStore(2);
        store.put(order("ORD-1", OrderStatus.CREATED));
        store.put(order("ORD-2", OrderStatus.CREATED));
        store.put(order("ORD-3", OrderStatus.CREATED));

        assertThat(store.size()).isEqualTo(2);
        assertThat(store.find("ORD-1")).isEmpty();
        assertThat(store.find("ORD-3")).isPresent();
    }

    @Test
    void activeOrdersExcludeTerminalOnes() {
        OrderStore store = new OrderStore();
        store.put(order("ORD-1", OrderStatus.PENDING));
        store.put(order("ORD-2", OrderStatus.FILLED));
        store.put(order("ORD-3", OrderStatus.PARTIALLY_FILLED));

        assertThat(store.activeOrders()).extracting(TradeOrder::orderId)
                .containsExactlyInAnyOrder("ORD-1", "ORD-3");
    }

    @Test
    void detectsADuplicateActiveOrder() {
        OrderStore store = new OrderStore();
        store.put(order("ORD-1", OrderStatus.PENDING));

        assertThat(store.hasActiveOrder(ExecutionTestFixtures.STRATEGY_ID,
                ExecutionTestFixtures.INSTRUMENT, OrderSide.BUY)).isTrue();
        assertThat(store.hasActiveOrder(ExecutionTestFixtures.STRATEGY_ID,
                ExecutionTestFixtures.INSTRUMENT, OrderSide.SELL)).isFalse();
        assertThat(store.hasActiveOrder("other", ExecutionTestFixtures.INSTRUMENT,
                OrderSide.BUY)).isFalse();
    }

    @Test
    void terminalOrdersDoNotCountAsDuplicates() {
        OrderStore store = new OrderStore();
        store.put(order("ORD-1", OrderStatus.FILLED));

        assertThat(store.hasActiveOrder(ExecutionTestFixtures.STRATEGY_ID,
                ExecutionTestFixtures.INSTRUMENT, OrderSide.BUY)).isFalse();
    }

    @Test
    void remembersTheBrokerOrderId() {
        OrderStore store = new OrderStore();
        store.put(order("ORD-1", OrderStatus.SUBMITTED));
        store.recordBrokerOrderId("ORD-1", "paper-7");

        assertThat(store.brokerOrderId("ORD-1")).contains("paper-7");
        assertThat(store.brokerOrderId("missing")).isEmpty();
        assertThat(store.brokerOrderId(null)).isEmpty();
    }

    @Test
    void ignoresBlankBrokerOrderIds() {
        OrderStore store = new OrderStore();
        store.recordBrokerOrderId("ORD-1", " ");

        assertThat(store.brokerOrderId("ORD-1")).isEmpty();
    }

    @Test
    void updatesStatusAndClears() {
        OrderStore store = new OrderStore();
        store.put(order("ORD-1", OrderStatus.PENDING));
        store.updateStatus("ORD-1", OrderStatus.CANCELLED);

        assertThat(store.find("ORD-1").orElseThrow().status())
                .isEqualTo(OrderStatus.CANCELLED);
        assertThat(store.active("ORD-1")).isFalse();

        store.clear();
        assertThat(store.size()).isZero();
    }

    @Test
    void rejectsAnUnusableCapacity() {
        assertThatThrownBy(() -> new OrderStore(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defaultCapacityIsBounded() {
        OrderStore store = new OrderStore();
        for (int index = 0; index < 300; index++) {
            store.put(order("ORD-" + index, OrderStatus.CREATED));
        }

        assertThat(store.size()).isEqualTo(256);
        assertThat(store.capacity()).isEqualTo(256);
    }
}
