package com.tradingassistant.pipeline.event;

/**
 * Publishes immutable pipeline events. Synchronous by design: the pipeline
 * completes an event's delivery before it continues to the next stage, which
 * makes event order deterministic and testable. A later phase can add an
 * asynchronous implementation behind the same interface.
 */
@FunctionalInterface
public interface MarketEventPublisher {

    void publish(MarketEvent event);

    /**
     * A publisher that discards every event, for wiring a pipeline without
     * consumers.
     */
    static MarketEventPublisher discarding() {
        return event -> {
        };
    }
}
