package com.tradingassistant.pipeline;

/**
 * Counters describing what one tick produced. Intended for tests, diagnostics
 * and future metrics, not for trading decisions.
 *
 * @param ticksAccepted      channels that merged the tick into a forming candle
 * @param candlesCompleted   candles closed by the tick
 * @param sessionsUpdated    indicator sessions advanced by the tick
 * @param eventsPublished    events published, including the tick event
 */
public record PipelineOutcome(
        int ticksAccepted,
        int candlesCompleted,
        int sessionsUpdated,
        int eventsPublished) {

    public PipelineOutcome {
        if (ticksAccepted < 0 || candlesCompleted < 0 || sessionsUpdated < 0 || eventsPublished < 0) {
            throw new IllegalArgumentException("Pipeline counters must not be negative");
        }
    }

    public boolean producedCandle() {
        return candlesCompleted > 0;
    }

    public static PipelineOutcome empty() {
        return new PipelineOutcome(0, 0, 0, 0);
    }
}
