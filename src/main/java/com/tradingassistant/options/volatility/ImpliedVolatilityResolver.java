package com.tradingassistant.options.volatility;

import com.tradingassistant.options.domain.ChainSnapshot;
import com.tradingassistant.options.domain.OptionLegKey;
import com.tradingassistant.options.domain.Strike;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves implied volatility for every leg that has a usable premium.
 *
 * <p>Legs whose premium cannot be reproduced by any configured volatility are
 * omitted rather than assigned a placeholder, so downstream analytics can see
 * exactly which observations exist.</p>
 */
public final class ImpliedVolatilityResolver {

    private final ImpliedVolatilitySolver solver;

    public ImpliedVolatilityResolver(ImpliedVolatilitySolver solver) {
        this.solver = Objects.requireNonNull(solver, "solver must not be null");
    }

    public Map<OptionLegKey, BigDecimal> resolve(ChainSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Map<OptionLegKey, BigDecimal> resolved = new LinkedHashMap<>();
        for (Strike leg : snapshot.legs()) {
            Optional<BigDecimal> premium = leg.premium();
            if (premium.isEmpty()) {
                continue;
            }
            solver.solve(leg.optionType(), snapshot.spotPrice(), leg.strike(), leg.expiry(),
                            snapshot.capturedAt(), premium.get())
                    .ifPresent(volatility -> resolved.put(OptionLegKey.of(leg), volatility));
        }
        return Map.copyOf(resolved);
    }
}
