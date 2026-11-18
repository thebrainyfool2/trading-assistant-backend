package com.tradingassistant.pipeline.event;

import com.tradingassistant.market.domain.Candle;

import java.time.Instant;
import java.util.Objects;

/**
 * A candle closed. Published once per completed candle and never for an empty
 * interval.
 *
 * @param candle     immutable completed candle
 * @param occurredAt application time of publication
 */
public record CandleCompletedEvent(Candle candle, Instant occurredAt) implements MarketEvent {

    public CandleCompletedEvent {
        candle = Objects.requireNonNull(candle, "candle must not be null");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    @Override
    public MarketEventType type() {
        return MarketEventType.CANDLE_COMPLETED;
    }
}
