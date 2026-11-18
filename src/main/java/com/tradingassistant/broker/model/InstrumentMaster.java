package com.tradingassistant.broker.model;

import java.time.Instant;
import java.util.List;

/**
 * A point-in-time instrument master download. This is intentionally not cached
 * in Phase 2; callers decide when a refresh is required.
 */
public record InstrumentMaster(List<Instrument> instruments, Instant retrievedAt) {

    public InstrumentMaster {
        instruments = instruments == null ? List.of() : List.copyOf(instruments);
    }

    public int size() {
        return instruments.size();
    }
}
