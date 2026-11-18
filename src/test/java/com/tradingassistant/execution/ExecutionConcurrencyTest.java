package com.tradingassistant.execution;

import com.tradingassistant.execution.domain.ExecutionResult;
import com.tradingassistant.execution.domain.ExecutionStatus;
import com.tradingassistant.execution.domain.OrderExecution;
import com.tradingassistant.execution.domain.OrderSide;
import com.tradingassistant.execution.domain.PortfolioSnapshot;
import com.tradingassistant.execution.domain.Position;
import com.tradingassistant.execution.domain.TradeOrder;
import com.tradingassistant.execution.order.OrderRequestFactory;
import com.tradingassistant.execution.order.OrderStore;
import com.tradingassistant.execution.portfolio.PortfolioService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionConcurrencyTest {

    @Test
    void concurrentFillsProduceAConsistentPosition() throws Exception {
        PortfolioService portfolio = new PortfolioService(ExecutionTestFixtures.config());
        int tasks = 20;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < tasks; index++) {
                String orderId = "ORD-" + index;
                futures.add(pool.submit(() -> {
                    start.await();
                    TradeOrder order = TradeOrder.created(ExecutionTestFixtures.request(),
                            orderId, ExecutionTestFixtures.START);
                    portfolio.applyFill(order, new OrderExecution("EX-" + orderId, orderId,
                            new BigDecimal("10"), new BigDecimal("500"),
                            ExecutionTestFixtures.START));
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        PortfolioSnapshot snapshot = portfolio.snapshot();
        assertThat(snapshot.openPositionCount()).isEqualTo(1);
        assertThat(snapshot.openPositions().getFirst().quantity())
                .isEqualByComparingTo("200");
        assertThat(snapshot.exposure()).isEqualByComparingTo("100000");
        assertThat(snapshot.usedMargin()).isEqualByComparingTo("20000");
        assertThat(snapshot.availableCapital()).isEqualByComparingTo("80000");
    }

    @Test
    void concurrentOrderStoreWritesRespectTheBound() throws Exception {
        OrderStore store = new OrderStore(32);
        int tasks = 100;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < tasks; index++) {
                String orderId = "ORD-" + index;
                futures.add(pool.submit(() -> {
                    start.await();
                    store.put(TradeOrder.created(ExecutionTestFixtures.request(), orderId,
                            ExecutionTestFixtures.START));
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(store.size()).isEqualTo(32);
        assertThat(store.activeOrders()).hasSize(32);
    }

    @Test
    void concurrentExecutionsRemainInternallyConsistent() throws Exception {
        ExecutionTestFixtures.Harness harness = ExecutionTestFixtures.harness();
        int tasks = 16;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ExecutionResult>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < tasks; index++) {
                OrderSide side = index % 2 == 0 ? OrderSide.BUY : OrderSide.SELL;
                futures.add(pool.submit(() -> {
                    start.await();
                    return harness.engine().execute(
                            new com.tradingassistant.execution.domain.ExecutionRequest(
                                    OrderRequestFactory.create(
                                            ExecutionTestFixtures.marketIntent(side),
                                            ExecutionTestFixtures.assessment()),
                                    ExecutionTestFixtures.assessment(),
                                    ExecutionTestFixtures.market()));
                }));
            }
            start.countDown();
            List<ExecutionResult> results = new ArrayList<>();
            for (Future<ExecutionResult> future : futures) {
                results.add(future.get(5, TimeUnit.SECONDS));
            }
            assertThat(results).hasSize(tasks);
            assertThat(results).allSatisfy(result -> assertThat(result.status()).isIn(
                    ExecutionStatus.COMPLETED, ExecutionStatus.PARTIAL,
                    ExecutionStatus.REJECTED));
        } finally {
            pool.shutdownNow();
        }

        Set<String> orderIds = ConcurrentHashMap.newKeySet();
        harness.sink().events().forEach(event -> {
            if (event instanceof com.tradingassistant.pipeline.event.OrderCreatedEvent created) {
                orderIds.add(created.order().orderId());
            }
        });
        assertThat(orderIds).hasSize(tasks);

        PortfolioSnapshot snapshot = harness.portfolio().snapshot();
        BigDecimal expected = snapshot.capital()
                .add(snapshot.realisedPnl())
                .subtract(snapshot.usedMargin())
                .subtract(snapshot.blockedMargin());
        assertThat(snapshot.availableCapital()).isEqualByComparingTo(expected);
        assertThat(snapshot.openPositions()).allSatisfy(position ->
                assertThat(position.closedQuantity())
                        .isLessThanOrEqualTo(position.quantity()));
    }

    @Test
    void concurrentMarksDoNotCorruptTheSnapshot() throws Exception {
        PortfolioService portfolio = new PortfolioService(ExecutionTestFixtures.config());
        TradeOrder order = TradeOrder.created(ExecutionTestFixtures.request(), "ORD-1",
                ExecutionTestFixtures.START);
        portfolio.applyFill(order, new OrderExecution("EX-1", "ORD-1",
                new BigDecimal("100"), new BigDecimal("500"), ExecutionTestFixtures.START));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<?>> futures = new ArrayList<>();
        try {
            IntStream.range(0, 200).forEach(index -> futures.add(pool.submit(() ->
                    portfolio.applyMark(ExecutionTestFixtures.INSTRUMENT,
                            new BigDecimal(500 + (index % 10))))));
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        PortfolioSnapshot snapshot = portfolio.snapshot();
        assertThat(snapshot.openPositions()).allSatisfy(position ->
                assertThat(position.lastPrice()).isPositive());
        assertThat(snapshot.unrealisedPnl().signum()).isGreaterThanOrEqualTo(0);
    }
}
