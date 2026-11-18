package com.tradingassistant.pipeline.session;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.market.domain.Candle;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Outcome of advancing one session by one candle. Results are keyed by
 * indicator type and include warm-up observations.
 *
 * @param session the updated session key
 * @param candle  candle that advanced the session
 * @param results indicator type to latest observation
 */
public record IndicatorSessionUpdate(
        IndicatorSessionKey session,
        Candle candle,
        Map<String, IndicatorResult> results) {

    public IndicatorSessionUpdate {
        session = Objects.requireNonNull(session, "session must not be null");
        candle = Objects.requireNonNull(candle, "candle must not be null");
        results = results == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(results));
    }

    public int indicatorCount() {
        return results.size();
    }
}
