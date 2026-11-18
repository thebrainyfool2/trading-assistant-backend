package com.tradingassistant.pipeline.event;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.pipeline.session.IndicatorSessionKey;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Every indicator in one session advanced by one candle. The map is keyed by
 * indicator type and holds immutable observations; warm-up observations are
 * included because they are facts about the session.
 *
 * @param session    session that produced the results
 * @param candle     candle that advanced the session
 * @param results    indicator type to latest observation
 * @param occurredAt application time of publication
 */
public record IndicatorUpdatedEvent(
        IndicatorSessionKey session,
        Candle candle,
        Map<String, IndicatorResult> results,
        Instant occurredAt) implements MarketEvent {

    public IndicatorUpdatedEvent {
        session = Objects.requireNonNull(session, "session must not be null");
        candle = Objects.requireNonNull(candle, "candle must not be null");
        results = results == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(results));
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    @Override
    public MarketEventType type() {
        return MarketEventType.INDICATOR_UPDATED;
    }
}
