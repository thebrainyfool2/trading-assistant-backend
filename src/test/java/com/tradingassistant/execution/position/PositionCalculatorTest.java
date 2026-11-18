package com.tradingassistant.execution.position;

import com.tradingassistant.execution.ExecutionTestFixtures;
import com.tradingassistant.execution.domain.OrderExecution;
import com.tradingassistant.execution.domain.OrderSide;
import com.tradingassistant.execution.domain.Position;
import com.tradingassistant.execution.domain.PositionDirection;
import com.tradingassistant.execution.domain.PositionStatus;
import com.tradingassistant.execution.domain.TradeOrder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PositionCalculatorTest {

    private static final Instant LATER = ExecutionTestFixtures.START.plusSeconds(60);

    private static Position position(PositionDirection direction, String quantity,
                                     String average) {
        return new Position("POS-1", ExecutionTestFixtures.STRATEGY_ID,
                ExecutionTestFixtures.INSTRUMENT, direction, new BigDecimal(quantity),
                BigDecimal.ZERO, new BigDecimal(average), Optional.of(new BigDecimal("490")),
                Optional.empty(), BigDecimal.ZERO, new BigDecimal(average),
                PositionStatus.OPEN, ExecutionTestFixtures.START,
                ExecutionTestFixtures.START, Optional.empty());
    }

    private static TradeOrder order() {
        return TradeOrder.created(ExecutionTestFixtures.request(), "ORD-1",
                ExecutionTestFixtures.START);
    }

    @Test
    void opensALongFromABuy() {
        Position opened = PositionCalculator.open("POS-1", order(), new BigDecimal("100"),
                new BigDecimal("500"), ExecutionTestFixtures.START);

        assertThat(opened.direction()).isEqualTo(PositionDirection.LONG);
        assertThat(opened.quantity()).isEqualByComparingTo("100");
        assertThat(opened.closedQuantity()).isZero();
        assertThat(opened.averageEntryPrice()).isEqualByComparingTo("500");
        assertThat(opened.stopPrice()).contains(new BigDecimal("490"));
        assertThat(opened.status()).isEqualTo(PositionStatus.OPEN);
        assertThat(opened.remainingQuantity()).isEqualByComparingTo("100");
        assertThat(opened.closedAt()).isEmpty();
    }

    @Test
    void directionFollowsTheOrderSide() {
        assertThat(PositionCalculator.direction(OrderSide.BUY))
                .isEqualTo(PositionDirection.LONG);
        assertThat(PositionCalculator.direction(OrderSide.SELL))
                .isEqualTo(PositionDirection.SHORT);
        assertThat(PositionDirection.SHORT.signum()).isEqualTo(-1);
        assertThat(PositionDirection.LONG.signum()).isEqualTo(1);
    }

    @Test
    void addingUpdatesTheWeightedAverage() {
        Position position = position(PositionDirection.LONG, "100", "500");
        Position updated = PositionCalculator.add(position, new BigDecimal("100"),
                new BigDecimal("520"), LATER);

        assertThat(updated.quantity()).isEqualByComparingTo("200");
        assertThat(updated.averageEntryPrice()).isEqualByComparingTo("510");
        assertThat(updated.lastPrice()).isEqualByComparingTo("520");
        assertThat(updated.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void addingKeepsTheClosedQuantity() {
        Position position = new Position("POS-1", ExecutionTestFixtures.STRATEGY_ID,
                ExecutionTestFixtures.INSTRUMENT, PositionDirection.LONG,
                new BigDecimal("100"), new BigDecimal("40"), new BigDecimal("500"),
                Optional.empty(), Optional.empty(), new BigDecimal("50"),
                new BigDecimal("500"), PositionStatus.PARTIALLY_CLOSED,
                ExecutionTestFixtures.START, ExecutionTestFixtures.START, Optional.empty());

        Position updated = PositionCalculator.add(position, new BigDecimal("100"),
                new BigDecimal("500"), LATER);

        assertThat(updated.closedQuantity()).isEqualByComparingTo("40");
        assertThat(updated.status()).isEqualTo(PositionStatus.PARTIALLY_CLOSED);
    }

    @Test
    void closingALongAtAHigherPriceRealisesAProfit() {
        var result = PositionCalculator.close(position(PositionDirection.LONG, "100", "500"),
                new BigDecimal("100"), new BigDecimal("520"), LATER);

        assertThat(result.realisedPnl()).isEqualByComparingTo("2000");
        assertThat(result.position().status()).isEqualTo(PositionStatus.CLOSED);
        assertThat(result.position().closedAt()).contains(LATER);
        assertThat(result.position().remainingQuantity()).isZero();
    }

    @Test
    void closingALongAtALowerPriceRealisesALoss() {
        var result = PositionCalculator.close(position(PositionDirection.LONG, "100", "500"),
                new BigDecimal("50"), new BigDecimal("480"), LATER);

        assertThat(result.realisedPnl()).isEqualByComparingTo("-1000");
        assertThat(result.position().status()).isEqualTo(PositionStatus.PARTIALLY_CLOSED);
        assertThat(result.position().closedAt()).isEmpty();
    }

    @Test
    void closingAShortProfitsWhenPriceFalls() {
        var result = PositionCalculator.close(position(PositionDirection.SHORT, "50", "500"),
                new BigDecimal("50"), new BigDecimal("480"), LATER);

        assertThat(result.realisedPnl()).isEqualByComparingTo("1000");
        assertThat(result.position().status()).isEqualTo(PositionStatus.CLOSED);
    }

    @Test
    void closingAShortLosesWhenPriceRises() {
        var result = PositionCalculator.close(position(PositionDirection.SHORT, "50", "500"),
                new BigDecimal("20"), new BigDecimal("520"), LATER);

        assertThat(result.realisedPnl()).isEqualByComparingTo("-400");
        assertThat(result.position().closedQuantity()).isEqualByComparingTo("20");
    }

    @Test
    void closingIsCappedAtTheRemainingQuantity() {
        var result = PositionCalculator.close(position(PositionDirection.LONG, "100", "500"),
                new BigDecimal("500"), new BigDecimal("510"), LATER);

        assertThat(result.position().closedQuantity()).isEqualByComparingTo("100");
        assertThat(result.realisedPnl()).isEqualByComparingTo("1000");
        assertThat(result.position().status()).isEqualTo(PositionStatus.CLOSED);
    }

    @Test
    void profitAccumulatesAcrossPartialCloses() {
        var first = PositionCalculator.close(position(PositionDirection.LONG, "100", "500"),
                new BigDecimal("40"), new BigDecimal("510"), LATER);
        var second = PositionCalculator.close(first.position(), new BigDecimal("10"),
                new BigDecimal("490"), LATER);

        assertThat(second.position().realisedPnl()).isEqualByComparingTo("300");
        assertThat(second.position().closedQuantity()).isEqualByComparingTo("50");
        assertThat(second.position().status()).isEqualTo(PositionStatus.PARTIALLY_CLOSED);
    }

    @Test
    void unrealisedProfitAndLossIsSignedByDirection() {
        Position longPosition = position(PositionDirection.LONG, "10", "100");
        Position shortPosition = position(PositionDirection.SHORT, "10", "100");

        assertThat(longPosition.unrealisedPnl(new BigDecimal("110")))
                .isEqualByComparingTo("100");
        assertThat(shortPosition.unrealisedPnl(new BigDecimal("110")))
                .isEqualByComparingTo("-100");
        assertThat(longPosition.notional()).isEqualByComparingTo("1000");
    }

    @Test
    void executionRecordCarriesTheFillNotional() {
        OrderExecution execution = new OrderExecution("EX-1", "ORD-1",
                new BigDecimal("10"), new BigDecimal("500"), LATER);

        assertThat(execution.notional()).isEqualByComparingTo("5000");
    }
}
