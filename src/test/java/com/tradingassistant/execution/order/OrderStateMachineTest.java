package com.tradingassistant.execution.order;

import com.tradingassistant.execution.domain.OrderStatus;
import com.tradingassistant.execution.exception.ExecutionValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStateMachineTest {

    @Test
    void createdCanOnlyBeValidatedOrTerminated() {
        assertThat(OrderStateMachine.canTransition(OrderStatus.CREATED,
                OrderStatus.VALIDATED)).isTrue();
        assertThat(OrderStateMachine.canTransition(OrderStatus.CREATED,
                OrderStatus.REJECTED)).isTrue();
        assertThat(OrderStateMachine.canTransition(OrderStatus.CREATED,
                OrderStatus.FAILED)).isTrue();
        assertThat(OrderStateMachine.canTransition(OrderStatus.CREATED,
                OrderStatus.SUBMITTED)).isFalse();
        assertThat(OrderStateMachine.canTransition(OrderStatus.CREATED,
                OrderStatus.FILLED)).isFalse();
    }

    @Test
    void validatedCanBeSubmittedOrTerminated() {
        assertThat(OrderStateMachine.canTransition(OrderStatus.VALIDATED,
                OrderStatus.SUBMITTED)).isTrue();
        assertThat(OrderStateMachine.canTransition(OrderStatus.VALIDATED,
                OrderStatus.PENDING)).isFalse();
        assertThat(OrderStateMachine.canTransition(OrderStatus.VALIDATED,
                OrderStatus.CANCELLED)).isFalse();
    }

    @Test
    void submittedCanReachEveryWorkingAndTerminalState() {
        for (OrderStatus target : new OrderStatus[]{OrderStatus.PENDING,
                OrderStatus.PARTIALLY_FILLED, OrderStatus.FILLED, OrderStatus.REJECTED,
                OrderStatus.FAILED, OrderStatus.CANCELLED, OrderStatus.EXPIRED}) {
            assertThat(OrderStateMachine.canTransition(OrderStatus.SUBMITTED, target))
                    .as("submitted to %s", target).isTrue();
        }
    }

    @Test
    void partiallyFilledCanKeepFilling() {
        assertThat(OrderStateMachine.canTransition(OrderStatus.PARTIALLY_FILLED,
                OrderStatus.PARTIALLY_FILLED)).isTrue();
        assertThat(OrderStateMachine.canTransition(OrderStatus.PARTIALLY_FILLED,
                OrderStatus.FILLED)).isTrue();
        assertThat(OrderStateMachine.canTransition(OrderStatus.PARTIALLY_FILLED,
                OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStateMachine.canTransition(OrderStatus.PARTIALLY_FILLED,
                OrderStatus.VALIDATED)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"FILLED", "CANCELLED", "REJECTED",
            "FAILED", "EXPIRED"})
    void terminalStatesAreFinal(OrderStatus terminal) {
        for (OrderStatus target : OrderStatus.values()) {
            assertThat(OrderStateMachine.canTransition(terminal, target))
                    .as("%s to %s", terminal, target).isFalse();
        }
    }

    @Test
    void nullStatesAreNeverAllowed() {
        assertThat(OrderStateMachine.canTransition(null, OrderStatus.CREATED)).isFalse();
        assertThat(OrderStateMachine.canTransition(OrderStatus.CREATED, null)).isFalse();
        assertThat(OrderStateMachine.canTransition(null, null)).isFalse();
    }

    @Test
    void requireTransitionAcceptsLegalMoves() {
        assertThatCode(() -> OrderStateMachine.requireTransition(OrderStatus.VALIDATED,
                OrderStatus.SUBMITTED)).doesNotThrowAnyException();
    }

    @Test
    void requireTransitionRejectsIllegalMovesNamingBothStates() {
        assertThatThrownBy(() -> OrderStateMachine.requireTransition(OrderStatus.FILLED,
                OrderStatus.CANCELLED))
                .isInstanceOf(ExecutionValidationException.class)
                .hasMessageContaining("FILLED")
                .hasMessageContaining("CANCELLED");
    }

    @Test
    void activeAndTerminalPartitionTheLifecycle() {
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(status.active()).isNotEqualTo(status.terminal());
        }
    }
}
