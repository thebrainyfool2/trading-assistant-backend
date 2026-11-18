package com.tradingassistant.execution.portfolio;

import com.tradingassistant.execution.ExecutionTestFixtures;
import com.tradingassistant.execution.domain.OrderExecution;
import com.tradingassistant.execution.domain.OrderSide;
import com.tradingassistant.execution.domain.PortfolioSnapshot;
import com.tradingassistant.execution.domain.PortfolioSummary;
import com.tradingassistant.execution.domain.PositionStatus;
import com.tradingassistant.execution.domain.TradeOrder;
import com.tradingassistant.execution.order.OrderRequestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioServiceTest {

    private static final Instant LATER = ExecutionTestFixtures.START.plusSeconds(60);

    private PortfolioService portfolio;

    @BeforeEach
    void setUp() {
        portfolio = new PortfolioService(ExecutionTestFixtures.config());
    }

    private static TradeOrder buyOrder() {
        return TradeOrder.created(ExecutionTestFixtures.request(), "ORD-1",
                ExecutionTestFixtures.START);
    }

    private static TradeOrder sellOrder() {
        return TradeOrder.created(OrderRequestFactory.create(
                        ExecutionTestFixtures.marketIntent(OrderSide.SELL),
                        ExecutionTestFixtures.assessment()),
                "ORD-2", LATER);
    }

    private static OrderExecution fill(String orderId, String quantity, String price) {
        return new OrderExecution("EX-" + orderId, orderId, new BigDecimal(quantity),
                new BigDecimal(price), LATER);
    }

    @Test
    void startsEmptyWithTheReferenceCapital() {
        PortfolioSnapshot snapshot = portfolio.snapshot();

        assertThat(snapshot.capital()).isEqualByComparingTo("100000");
        assertThat(snapshot.availableCapital()).isEqualByComparingTo("100000");
        assertThat(snapshot.blockedMargin()).isZero();
        assertThat(snapshot.usedMargin()).isZero();
        assertThat(snapshot.exposure()).isZero();
        assertThat(snapshot.realisedPnl()).isZero();
        assertThat(snapshot.unrealisedPnl()).isZero();
        assertThat(snapshot.openPositionCount()).isZero();
        assertThat(snapshot.closedPositionCount()).isZero();
    }

    @Test
    void blockingReservesMarginFromAvailableCapital() {
        portfolio.block("ORD-1", new BigDecimal("10000"));

        assertThat(portfolio.snapshot().blockedMargin()).isEqualByComparingTo("10000");
        assertThat(portfolio.snapshot().availableCapital()).isEqualByComparingTo("90000");
        assertThat(portfolio.blockedMargin("ORD-1")).contains(new BigDecimal("10000"));
        assertThat(portfolio.blockedMarginTotal()).isEqualByComparingTo("10000");
    }

    @Test
    void releaseReturnsTheReservedMargin() {
        portfolio.block("ORD-1", new BigDecimal("10000"));
        portfolio.release("ORD-1");

        assertThat(portfolio.snapshot().blockedMargin()).isZero();
        assertThat(portfolio.snapshot().availableCapital()).isEqualByComparingTo("100000");
        assertThat(portfolio.blockedMargin("ORD-1")).isEmpty();
    }

    @Test
    void aFillRemovesTheBlockOfItsOrder() {
        portfolio.block("ORD-1", new BigDecimal("10000"));

        portfolio.applyFill(buyOrder(), fill("ORD-1", "100", "500"));

        assertThat(portfolio.snapshot().blockedMargin()).isZero();
    }

    @Test
    void openingAFillCreatesAPositionAndConsumesMargin() {
        PortfolioChange change = portfolio.applyFill(buyOrder(), fill("ORD-1", "100", "500"));

        assertThat(change.opened()).isTrue();
        assertThat(change.modified()).isFalse();
        assertThat(change.closed()).isFalse();
        assertThat(change.status()).isEqualTo(PositionStatus.OPEN);
        assertThat(change.position().averageEntryPrice()).isEqualByComparingTo("500");
        assertThat(portfolio.openPositionCount()).isEqualTo(1);
        assertThat(portfolio.snapshot().exposure()).isEqualByComparingTo("50000");
        assertThat(portfolio.snapshot().usedMargin()).isEqualByComparingTo("10000");
        assertThat(portfolio.snapshot().availableCapital()).isEqualByComparingTo("90000");
    }

    @Test
    void aSecondFillInTheSameDirectionAddsToThePosition() {
        portfolio.applyFill(buyOrder(), fill("ORD-1", "100", "500"));
        PortfolioChange change = portfolio.applyFill(buyOrder(), fill("ORD-1", "100", "520"));

        assertThat(change.opened()).isFalse();
        assertThat(change.modified()).isTrue();
        assertThat(change.closed()).isFalse();
        assertThat(change.position().quantity()).isEqualByComparingTo("200");
        assertThat(change.position().averageEntryPrice()).isEqualByComparingTo("510");
        assertThat(portfolio.openPositionCount()).isEqualTo(1);
        assertThat(portfolio.snapshot().exposure()).isEqualByComparingTo("102000");
    }

    @Test
    void anOppositeFillClosesAndRealisesProfit() {
        portfolio.applyFill(buyOrder(), fill("ORD-1", "100", "500"));
        PortfolioChange change = portfolio.applyFill(sellOrder(), fill("ORD-2", "100", "520"));

        assertThat(change.closed()).isTrue();
        assertThat(change.position().status()).isEqualTo(PositionStatus.CLOSED);
        assertThat(change.position().realisedPnl()).isEqualByComparingTo("2000");
        assertThat(change.snapshot().realisedPnl()).isEqualByComparingTo("2000");
        assertThat(change.snapshot().availableCapital()).isEqualByComparingTo("102000");
        assertThat(change.snapshot().openPositionCount()).isZero();
        assertThat(change.snapshot().closedPositionCount()).isEqualTo(1);
    }

    @Test
    void anOppositeFillCanCloseOnlyPartOfThePosition() {
        portfolio.applyFill(buyOrder(), fill("ORD-1", "100", "500"));
        PortfolioChange change = portfolio.applyFill(sellOrder(), fill("ORD-2", "40", "510"));

        assertThat(change.closed()).isFalse();
        assertThat(change.status()).isEqualTo(PositionStatus.PARTIALLY_CLOSED);
        assertThat(change.position().closedQuantity()).isEqualByComparingTo("40");
        assertThat(change.position().remainingQuantity()).isEqualByComparingTo("60");
        assertThat(change.snapshot().realisedPnl()).isEqualByComparingTo("400");
        assertThat(change.snapshot().openPositionCount()).isEqualTo(1);
    }

    @Test
    void marksDriveUnrealisedProfitAndLoss() {
        portfolio.applyFill(buyOrder(), fill("ORD-1", "100", "500"));

        portfolio.applyMark(ExecutionTestFixtures.INSTRUMENT, new BigDecimal("510"));

        assertThat(portfolio.snapshot().unrealisedPnl()).isEqualByComparingTo("1000");
        assertThat(portfolio.snapshot().exposure()).isEqualByComparingTo("50000");
    }

    @Test
    void anInvalidMarkIsIgnored() {
        portfolio.applyFill(buyOrder(), fill("ORD-1", "100", "500"));
        portfolio.applyMark(ExecutionTestFixtures.INSTRUMENT, BigDecimal.ZERO);
        portfolio.applyMark(null, new BigDecimal("510"));

        assertThat(portfolio.snapshot().unrealisedPnl()).isZero();
    }

    @Test
    void marksOfClosedPositionsCarryNoProfitAndLoss() {
        portfolio.applyFill(buyOrder(), fill("ORD-1", "100", "500"));
        portfolio.applyFill(sellOrder(), fill("ORD-2", "100", "520"));

        portfolio.applyMark(ExecutionTestFixtures.INSTRUMENT, new BigDecimal("600"));

        assertThat(portfolio.snapshot().unrealisedPnl()).isZero();
        assertThat(portfolio.snapshot().realisedPnl()).isEqualByComparingTo("2000");
    }

    @Test
    void findsTheOpenPositionOfAStrategyAndInstrument() {
        portfolio.applyFill(buyOrder(), fill("ORD-1", "100", "500"));

        assertThat(portfolio.position(ExecutionTestFixtures.STRATEGY_ID,
                ExecutionTestFixtures.INSTRUMENT)).isPresent();
        assertThat(portfolio.position("other", ExecutionTestFixtures.INSTRUMENT)).isEmpty();
        assertThat(portfolio.position(null, null)).isEmpty();
    }

    @Test
    void reportsTheClosingSide() {
        portfolio.applyFill(buyOrder(), fill("ORD-1", "100", "500"));

        assertThat(portfolio.closingSide(ExecutionTestFixtures.STRATEGY_ID,
                ExecutionTestFixtures.INSTRUMENT)).contains(OrderSide.SELL);
        assertThat(portfolio.closingSide("other", ExecutionTestFixtures.INSTRUMENT))
                .isEmpty();
    }

    @Test
    void summaryDescribesTheCurrentState() {
        portfolio.applyFill(buyOrder(), fill("ORD-1", "100", "500"));
        PortfolioSummary summary = portfolio.summary();

        assertThat(summary.openPositions()).isEqualTo(1);
        assertThat(summary.exposure()).isEqualByComparingTo("50000");
        assertThat(summary.usedMargin()).isEqualByComparingTo("10000");
        assertThat(summary.availableCapital()).isEqualByComparingTo("90000");
    }

    @Test
    void averageOpenNotionalIsZeroWithoutPositions() {
        assertThat(portfolio.averageOpenNotional()).isZero();
    }

    @Test
    void averageOpenNotionalAveragesTheExposure() {
        portfolio.applyFill(buyOrder(), fill("ORD-1", "100", "500"));

        assertThat(portfolio.averageOpenNotional()).isEqualByComparingTo("50000");
    }

    @Test
    void clearingRemovesPositionsAndMargins() {
        portfolio.block("ORD-1", new BigDecimal("1000"));
        portfolio.applyFill(buyOrder(), fill("ORD-1", "100", "500"));

        portfolio.clear();

        assertThat(portfolio.snapshot().openPositionCount()).isZero();
        assertThat(portfolio.snapshot().blockedMargin()).isZero();
        assertThat(portfolio.snapshot().realisedPnl()).isZero();
    }

    @Test
    void exposesTheConfigurationItFollows() {
        assertThat(portfolio.config()).isEqualTo(ExecutionTestFixtures.config());
    }
}
