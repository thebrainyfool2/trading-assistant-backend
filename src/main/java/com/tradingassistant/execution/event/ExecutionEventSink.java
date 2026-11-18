package com.tradingassistant.execution.event;

import com.tradingassistant.pipeline.event.MarketEvent;

/**
 * Receives the pipeline events an execution attempt produces.
 *
 * <p>The engine emits stage events through this contract; the service supplies one
 * that publishes to the event bus, while tests supply a recording sink. A
 * discarding sink exists so an engine can be wired without a pipeline.</p>
 */
@FunctionalInterface
public interface ExecutionEventSink {

    void publish(MarketEvent event);

    /**
     * A sink that discards every event.
     */
    static ExecutionEventSink discarding() {
        return event -> {
        };
    }
}
