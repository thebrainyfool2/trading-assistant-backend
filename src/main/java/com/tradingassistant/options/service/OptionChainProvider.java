package com.tradingassistant.options.service;

import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.options.domain.ChainSnapshot;

import java.time.Instant;
import java.util.Optional;

/**
 * Supplies an immutable chain capture for a spot instrument.
 *
 * <p>The contract exists so the analytics service never depends on how option
 * contracts and quotes reach the application. A feed-backed provider can replace
 * the in-memory one without touching the engine.</p>
 */
public interface OptionChainProvider {

    /**
     * Chain capture for the given underlying at the given instant, or empty when
     * the instrument has no registered option chain.
     */
    Optional<ChainSnapshot> snapshotFor(InstrumentKey spotInstrument, Instant capturedAt);
}
