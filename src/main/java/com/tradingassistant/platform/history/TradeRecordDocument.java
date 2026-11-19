package com.tradingassistant.platform.history;

import com.tradingassistant.execution.domain.PositionDirection;
import com.tradingassistant.execution.domain.PositionStatus;
import com.tradingassistant.execution.domain.TradeLifecycle;
import com.tradingassistant.market.domain.Segment;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Durable record of a completed trade lifecycle event.
 *
 * <p>One document is stored per distinct lifecycle update, keyed by order and
 * update instant so a redelivered event cannot duplicate history.</p>
 */
@Document(collection = "trades")
public record TradeRecordDocument(
        @Id String id,
        @Indexed String userId,
        @Indexed String tradeKey,
        String orderId,
        String positionId,
        String strategyId,
        Segment segment,
        String symbolToken,
        PositionDirection direction,
        PositionStatus positionStatus,
        BigDecimal filledQuantity,
        BigDecimal averageEntryPrice,
        BigDecimal closedQuantity,
        BigDecimal realisedPnl,
        Instant openedAt,
        Instant updatedAt,
        Instant closedAt,
        Instant recordedAt) {

    public TradeRecordDocument {
        userId = Objects.requireNonNull(userId, "userId must not be null");
        tradeKey = Objects.requireNonNull(tradeKey, "tradeKey must not be null");
        orderId = Objects.requireNonNull(orderId, "orderId must not be null");
        positionId = Objects.requireNonNull(positionId, "positionId must not be null");
        strategyId = Objects.requireNonNull(strategyId, "strategyId must not be null");
        segment = Objects.requireNonNull(segment, "segment must not be null");
        symbolToken = Objects.requireNonNull(symbolToken, "symbolToken must not be null");
        direction = Objects.requireNonNull(direction, "direction must not be null");
        positionStatus = Objects.requireNonNull(positionStatus,
                "positionStatus must not be null");
        filledQuantity = Objects.requireNonNull(filledQuantity,
                "filledQuantity must not be null");
        averageEntryPrice = Objects.requireNonNull(averageEntryPrice,
                "averageEntryPrice must not be null");
        closedQuantity = Objects.requireNonNull(closedQuantity,
                "closedQuantity must not be null");
        realisedPnl = Objects.requireNonNull(realisedPnl, "realisedPnl must not be null");
        openedAt = Objects.requireNonNull(openedAt, "openedAt must not be null");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        recordedAt = Objects.requireNonNull(recordedAt, "recordedAt must not be null");
    }

    public static TradeRecordDocument from(String userId, TradeLifecycle lifecycle,
                                           Instant recordedAt) {
        return new TradeRecordDocument(null, userId,
                lifecycle.orderId() + ':' + lifecycle.updatedAt(),
                lifecycle.orderId(), lifecycle.positionId(), lifecycle.strategyId(),
                lifecycle.instrument().segment(), lifecycle.instrument().symbolToken(),
                lifecycle.direction(), lifecycle.positionStatus(),
                lifecycle.filledQuantity(), lifecycle.averageEntryPrice(),
                lifecycle.closedQuantity(), lifecycle.realisedPnl(),
                lifecycle.openedAt(), lifecycle.updatedAt(),
                lifecycle.closedAt().orElse(null), recordedAt);
    }

    public static String key(TradeLifecycle lifecycle) {
        return lifecycle.orderId() + ':' + lifecycle.updatedAt();
    }
}
