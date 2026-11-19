package com.tradingassistant.platform.api.dto;

import com.tradingassistant.execution.domain.ExecutionStatus;
import com.tradingassistant.execution.domain.OrderSide;
import com.tradingassistant.execution.domain.OrderStatus;
import com.tradingassistant.execution.domain.OrderType;
import com.tradingassistant.execution.domain.TimeInForce;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.platform.executionapp.OrderApplicationService;
import com.tradingassistant.platform.history.OrderRecordDocument;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Order placement and history contract.
 *
 * <p>The request states the direction explicitly. Quantity is deliberately
 * absent: it comes from the engine's risk assessment and cannot be supplied or
 * overridden by a client.</p>
 */
public final class OrderDtos {

    private OrderDtos() {
    }

    public record PlaceOrderRequest(
            @NotBlank @Size(max = 64) String strategyId,
            @NotNull Segment segment,
            @NotBlank @Size(max = 64) String symbolToken,
            @NotNull OrderSide side,
            @NotNull OrderType type,
            @Positive BigDecimal limitPrice,
            @Positive BigDecimal triggerPrice,
            @Positive BigDecimal targetPrice,
            TimeInForce timeInForce) {

        public OrderApplicationService.PlaceOrderCommand toCommand() {
            return new OrderApplicationService.PlaceOrderCommand(strategyId, segment,
                    symbolToken, side, type, Optional.ofNullable(limitPrice),
                    Optional.ofNullable(triggerPrice),
                    Optional.ofNullable(targetPrice),
                    timeInForce == null ? TimeInForce.DAY : timeInForce);
        }
    }

    public record OrderView(
            String id,
            String engineOrderId,
            String strategyId,
            Segment segment,
            String symbolToken,
            OrderSide side,
            OrderType type,
            BigDecimal quantity,
            BigDecimal filledQuantity,
            BigDecimal limitPrice,
            BigDecimal triggerPrice,
            BigDecimal stopPrice,
            BigDecimal targetPrice,
            TimeInForce timeInForce,
            OrderStatus status,
            BigDecimal averageFillPrice,
            ExecutionStatus executionStatus,
            String reason,
            String positionId,
            Instant createdAt,
            Instant updatedAt) {

        public static OrderView from(OrderRecordDocument order) {
            return new OrderView(order.id(), order.engineOrderId(),
                    order.strategyId(), order.segment(), order.symbolToken(),
                    order.side(), order.type(), order.quantity(),
                    order.filledQuantity(), order.limitPrice(),
                    order.triggerPrice(), order.stopPrice(), order.targetPrice(),
                    order.timeInForce(), order.status(), order.averageFillPrice(),
                    order.executionStatus(), order.reason(), order.positionId(),
                    order.createdAt(), order.updatedAt());
        }
    }
}
