package com.tradingassistant.execution.order;

import com.tradingassistant.execution.ExecutionTestFixtures;
import com.tradingassistant.execution.domain.OrderIntent;
import com.tradingassistant.execution.domain.OrderRequest;
import com.tradingassistant.execution.domain.OrderSide;
import com.tradingassistant.execution.domain.OrderType;
import com.tradingassistant.execution.domain.TimeInForce;
import com.tradingassistant.execution.exception.ExecutionValidationException;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.market.domain.InstrumentKey;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderRequestFactoryTest {

    @Test
    void takesQuantityAndStopFromTheRiskAssessment() {
        OrderRequest request = OrderRequestFactory.create(
                ExecutionTestFixtures.marketIntent(), ExecutionTestFixtures.assessment());

        assertThat(request.quantity()).isEqualByComparingTo("100");
        assertThat(request.stopPrice()).contains(new BigDecimal("490"));
        assertThat(request.type()).isEqualTo(OrderType.MARKET);
        assertThat(request.timeInForce()).isEqualTo(TimeInForce.DAY);
        assertThat(request.timestamp()).isEqualTo(ExecutionTestFixtures.START);
    }

    @Test
    void putsTheStopAboveTheReferenceForAShort() {
        OrderRequest request = OrderRequestFactory.create(
                ExecutionTestFixtures.marketIntent(OrderSide.SELL),
                ExecutionTestFixtures.assessment());

        assertThat(request.stopPrice()).contains(new BigDecimal("510"));
    }

    @Test
    void keepsLimitTriggerAndTargetFromTheIntent() {
        OrderIntent intent = new OrderIntent(ExecutionTestFixtures.STRATEGY_ID,
                ExecutionTestFixtures.INSTRUMENT, OrderSide.BUY, OrderType.STOP_LIMIT,
                Optional.of(new BigDecimal("499")), Optional.of(new BigDecimal("498")),
                Optional.of(new BigDecimal("520")), TimeInForce.GTC);

        OrderRequest request = OrderRequestFactory.create(intent,
                ExecutionTestFixtures.assessment());

        assertThat(request.limitPrice()).contains(new BigDecimal("499"));
        assertThat(request.triggerPrice()).contains(new BigDecimal("498"));
        assertThat(request.targetPrice()).contains(new BigDecimal("520"));
        assertThat(request.timeInForce()).isEqualTo(TimeInForce.GTC);
        assertThat(request.type()).isEqualTo(OrderType.STOP_LIMIT);
    }

    @Test
    void rejectsAStrategyMismatch() {
        OrderIntent intent = OrderIntent.market("OTHER", ExecutionTestFixtures.INSTRUMENT,
                OrderSide.BUY);

        assertThatThrownBy(() -> OrderRequestFactory.create(intent,
                ExecutionTestFixtures.assessment()))
                .isInstanceOf(ExecutionValidationException.class)
                .hasMessageContaining("strategyId");
    }

    @Test
    void rejectsAnInstrumentMismatch() {
        OrderIntent intent = OrderIntent.market(ExecutionTestFixtures.STRATEGY_ID,
                new InstrumentKey(Segment.NSE_CASH, "9999"), OrderSide.BUY);

        assertThatThrownBy(() -> OrderRequestFactory.create(intent,
                ExecutionTestFixtures.assessment()))
                .isInstanceOf(ExecutionValidationException.class)
                .hasMessageContaining("instrument");
    }

    @Test
    void rejectsNullArguments() {
        assertThatThrownBy(() -> OrderRequestFactory.create(null,
                ExecutionTestFixtures.assessment()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> OrderRequestFactory.create(
                ExecutionTestFixtures.marketIntent(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void protectiveStopStaysNonNegative() {
        assertThat(OrderRequestFactory.protectiveStop(OrderSide.BUY, new BigDecimal("5"),
                new BigDecimal("10"))).isZero();
        assertThat(OrderRequestFactory.protectiveStop(OrderSide.SELL, new BigDecimal("500"),
                new BigDecimal("10"))).isEqualByComparingTo("510");
    }

    @Test
    void marketIntentDefaultsToDayAndNoPrices() {
        OrderIntent intent = ExecutionTestFixtures.marketIntent();

        assertThat(intent.limitPrice()).isEmpty();
        assertThat(intent.triggerPrice()).isEmpty();
        assertThat(intent.targetPrice()).isEmpty();
        assertThat(intent.timeInForce()).isEqualTo(TimeInForce.DAY);
    }

    @Test
    void requestNotionalUsesTheReferencePrice() {
        OrderRequest request = ExecutionTestFixtures.request();

        assertThat(request.notional(new BigDecimal("500"))).isEqualByComparingTo("50000");
        assertThat(request.marketOrder()).isTrue();
    }
}
