package com.tradingassistant.options.domain;

import com.tradingassistant.intelligence.domain.AssessmentStatus;

import java.util.Objects;
import java.util.Optional;

/**
 * Greeks description of the chain.
 *
 * <p>{@code netExposure} is the open-interest-weighted sum of leg Greeks with
 * calls positive and puts negative. It describes the listed exposure in a
 * single number per Greek; it is not a position, because open interest counts
 * both sides of every contract.</p>
 */
public record GreeksSummary(
        Optional<Greeks> atmCall,
        Optional<Greeks> atmPut,
        Greeks netExposure,
        int evaluatedLegs,
        AssessmentStatus status,
        String summary) {

    public GreeksSummary {
        atmCall = atmCall == null ? Optional.empty() : atmCall;
        atmPut = atmPut == null ? Optional.empty() : atmPut;
        netExposure = Objects.requireNonNull(netExposure, "netExposure must not be null");
        if (evaluatedLegs < 0) {
            throw new IllegalArgumentException("evaluatedLegs must not be negative");
        }
        status = Objects.requireNonNull(status, "status must not be null");
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        summary = summary.trim();
    }
}
