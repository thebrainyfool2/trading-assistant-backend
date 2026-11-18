package com.tradingassistant.options.domain;

import com.tradingassistant.intelligence.domain.AssessmentStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Implied-volatility description of the nearest expiry and the prepared
 * surface.
 *
 * <p>Rank and percentile need a history of at least two observations and are
 * empty otherwise. Rank is the position of the current ATM volatility inside the
 * observed range in percent; percentile is the share of observations below the
 * current value. Neither is a forecast.</p>
 */
public record VolatilitySummary(
        Optional<BigDecimal> atmImpliedVolatility,
        Optional<BigDecimal> impliedVolatilityChange,
        Optional<BigDecimal> impliedVolatilityRank,
        Optional<BigDecimal> impliedVolatilityPercentile,
        Optional<BigDecimal> skew,
        VolatilityTrend trend,
        VolatilityState state,
        List<SmilePoint> smile,
        List<IvSurfacePoint> surface,
        AssessmentStatus status,
        String summary) {

    public VolatilitySummary {
        atmImpliedVolatility = atmImpliedVolatility == null ? Optional.empty()
                : atmImpliedVolatility;
        impliedVolatilityChange = impliedVolatilityChange == null ? Optional.empty()
                : impliedVolatilityChange;
        impliedVolatilityRank = impliedVolatilityRank == null ? Optional.empty()
                : impliedVolatilityRank;
        impliedVolatilityPercentile = impliedVolatilityPercentile == null ? Optional.empty()
                : impliedVolatilityPercentile;
        skew = skew == null ? Optional.empty() : skew;
        trend = Objects.requireNonNull(trend, "trend must not be null");
        state = Objects.requireNonNull(state, "state must not be null");
        smile = smile == null ? List.of() : List.copyOf(smile);
        surface = surface == null ? List.of() : List.copyOf(surface);
        status = Objects.requireNonNull(status, "status must not be null");
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        summary = summary.trim();
    }
}
