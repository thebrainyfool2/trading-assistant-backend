package com.tradingassistant.execution.broker;

import com.tradingassistant.execution.ExecutionTestFixtures;
import com.tradingassistant.execution.domain.ExecutionReport;
import com.tradingassistant.execution.domain.OrderSide;
import com.tradingassistant.execution.domain.OrderStatus;
import com.tradingassistant.execution.domain.OrderType;
import com.tradingassistant.execution.domain.Position;
import com.tradingassistant.execution.domain.TimeInForce;
import com.tradingassistant.execution.exception.ExecutionBrokerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaperBrokerAdapterTest {

    private PaperBrokerAdapter broker;

    @BeforeEach
    void setUp() {
        broker = new PaperBrokerAdapter(ExecutionTestFixtures.clock());
    }

    private static BrokerOrderCommand command(OrderType type,
                                              Optional<BigDecimal> limit) {
        return new BrokerOrderCommand("ORD-1", ExecutionTestFixtures.INSTRUMENT,
                OrderSide.BUY, type, new BigDecimal("100"), limit, Optional.empty(),
                TimeInForce.DAY, new BigDecimal("500"));
    }

    @Test
    void isNamedPaperAndStartsConnected() {
        assertThat(broker.brokerName()).isEqualTo("paper");
        assertThat(broker.connected()).isTrue();
    }

    @Test
    void aMarketOrderFillsAtTheReferencePrice() {
        ExecutionReport report = broker.placeOrder(command(OrderType.MARKET,
                Optional.empty()));

        assertThat(report.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(report.filledQuantity()).isEqualByComparingTo("100");
        assertThat(report.averagePrice()).contains(new BigDecimal("500"));
        assertThat(report.executions()).hasSize(1);
        assertThat(report.executions().getFirst().orderId()).isEqualTo("ORD-1");
        assertThat(report.brokerOrderId()).startsWith("paper-");
    }

    @Test
    void aLimitOrderFillsAtTheLimitPrice() {
        ExecutionReport report = broker.placeOrder(command(OrderType.LIMIT,
                Optional.of(new BigDecimal("499"))));

        assertThat(report.averagePrice()).contains(new BigDecimal("499"));
        assertThat(report.executions().getFirst().price())
                .isEqualByComparingTo("499");
    }

    @Test
    void aConfiguredRatioProducesAPartialFill() {
        broker.fillRatio(new BigDecimal("0.5"));

        ExecutionReport report = broker.placeOrder(command(OrderType.MARKET,
                Optional.empty()));

        assertThat(report.status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(report.filledQuantity()).isEqualByComparingTo("50");
    }

    @Test
    void rejectionCanBeConfigured() {
        broker.rejectAll(true);

        ExecutionReport report = broker.placeOrder(command(OrderType.MARKET,
                Optional.empty()));

        assertThat(report.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(report.filledQuantity()).isZero();
        assertThat(report.message().orElseThrow()).contains("rejected");
    }

    @Test
    void aDisconnectedBrokerRefusesOrdersWithARetryableError() {
        broker.connected(false);

        assertThatThrownBy(() -> broker.placeOrder(command(OrderType.MARKET,
                Optional.empty())))
                .isInstanceOf(ExecutionBrokerException.class)
                .hasMessageContaining("disconnected")
                .satisfies(error -> assertThat(((ExecutionBrokerException) error)
                        .retryable()).isTrue());
    }

    @Test
    void transientFailuresAreCountedDown() {
        broker.failNext(1);

        assertThatThrownBy(() -> broker.placeOrder(command(OrderType.MARKET,
                Optional.empty()))).isInstanceOf(ExecutionBrokerException.class);
        ExecutionReport report = broker.placeOrder(command(OrderType.MARKET,
                Optional.empty()));

        assertThat(report.status()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void severalTransientFailuresCanBeRequested() {
        broker.failNext(2);

        assertThatThrownBy(() -> broker.placeOrder(command(OrderType.MARKET,
                Optional.empty()))).isInstanceOf(ExecutionBrokerException.class);
        assertThatThrownBy(() -> broker.placeOrder(command(OrderType.MARKET,
                Optional.empty()))).isInstanceOf(ExecutionBrokerException.class);
        assertThat(broker.placeOrder(command(OrderType.MARKET, Optional.empty())).status())
                .isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void cancellationReportsACancelledOrder() {
        ExecutionReport placed = broker.placeOrder(command(OrderType.MARKET,
                Optional.empty()));

        ExecutionReport cancelled = broker.cancelOrder(placed.brokerOrderId());

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.filledQuantity()).isEqualByComparingTo("100");
    }

    @Test
    void modificationIsAcceptedAsPending() {
        ExecutionReport modified = broker.modifyOrder(command(OrderType.LIMIT,
                Optional.of(new BigDecimal("499"))));

        assertThat(modified.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(modified.message().orElseThrow()).contains("modification");
    }

    @Test
    void reportsKnownOrdersAndIgnoresUnknownOnes() {
        ExecutionReport placed = broker.placeOrder(command(OrderType.MARKET,
                Optional.empty()));

        assertThat(broker.orderStatus(placed.brokerOrderId())).isPresent();
        assertThat(broker.orderStatus("missing")).isEmpty();
    }

    @Test
    void positionsAreTheOnesRecordedByTheTest() {
        Position position = new Position("POS-1", ExecutionTestFixtures.STRATEGY_ID,
                ExecutionTestFixtures.INSTRUMENT,
                com.tradingassistant.execution.domain.PositionDirection.LONG,
                new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("500"),
                Optional.empty(), Optional.empty(), BigDecimal.ZERO,
                new BigDecimal("500"),
                com.tradingassistant.execution.domain.PositionStatus.OPEN,
                ExecutionTestFixtures.START, ExecutionTestFixtures.START, Optional.empty());
        broker.recordPosition(position);

        assertThat(broker.positions()).containsExactly(position);
    }

    @Test
    void reportsAreBoundedByTheBrokerMemory() {
        broker.placeOrder(command(OrderType.MARKET, Optional.empty()));
        broker.placeOrder(command(OrderType.MARKET, Optional.empty()));

        assertThat(broker.reportCount()).isEqualTo(2);
    }

    @Test
    void rejectsAnUnusableFillRatio() {
        assertThatThrownBy(() -> broker.fillRatio(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> broker.fillRatio(new BigDecimal("1.5")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> broker.fillRatio(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
