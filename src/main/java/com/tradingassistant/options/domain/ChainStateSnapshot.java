package com.tradingassistant.options.domain;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable per-leg state of an earlier capture. The analytics engine compares
 * the current chain with this snapshot to describe open-interest, volume and
 * volatility change; it never mutates either snapshot.
 */
public record ChainStateSnapshot(Map<OptionLegKey, LegState> legs) {

    public ChainStateSnapshot {
        legs = legs == null ? Map.of() : Map.copyOf(legs);
    }

    public static ChainStateSnapshot empty() {
        return new ChainStateSnapshot(Map.of());
    }

    public static ChainStateSnapshot of(ChainSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Map<OptionLegKey, LegState> legs = new java.util.HashMap<>();
        for (Strike leg : snapshot.legs()) {
            long openInterest = leg.openInterest().orElse(0L);
            long volume = leg.tradedVolume().orElse(0L);
            leg.premium().ifPresent(premium ->
                    legs.put(OptionLegKey.of(leg),
                            new LegState(openInterest, volume, premium, Optional.empty())));
        }
        return new ChainStateSnapshot(legs);
    }

    public Optional<LegState> leg(OptionLegKey key) {
        return Optional.ofNullable(legs.get(key));
    }

    public boolean isEmpty() {
        return legs.isEmpty();
    }

    public int size() {
        return legs.size();
    }
}
