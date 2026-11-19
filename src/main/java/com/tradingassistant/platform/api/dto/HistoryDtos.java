package com.tradingassistant.platform.api.dto;

import com.tradingassistant.execution.domain.PositionDirection;
import com.tradingassistant.execution.domain.PositionStatus;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.platform.history.TradeRecordDocument;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Completed trade history contract.
 */
public final class HistoryDtos {

    private HistoryDtos() {
    }

    public record TradeView(
            String id,
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

        public static TradeView from(TradeRecordDocument trade) {
            return new TradeView(trade.id(), trade.orderId(), trade.positionId(),
                    trade.strategyId(), trade.segment(), trade.symbolToken(),
                    trade.direction(), trade.positionStatus(), trade.filledQuantity(),
                    trade.averageEntryPrice(), trade.closedQuantity(),
                    trade.realisedPnl(), trade.openedAt(), trade.updatedAt(),
                    trade.closedAt(), trade.recordedAt());
        }
    }
}
