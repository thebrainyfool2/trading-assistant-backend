package com.tradingassistant.options.domain;

import com.tradingassistant.intelligence.domain.AssessmentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Max-pain description for one expiry: the strike with the smallest aggregate
 * payout, the full pain curve, and the strikes nearest to it.
 */
public record MaxPainSummary(
        Optional<LocalDate> expiry,
        Optional<BigDecimal> maxPainStrike,
        List<PainPoint> curve,
        List<PainPoint> nearestStrikes,
        AssessmentStatus status,
        String summary) {

    public MaxPainSummary {
        expiry = expiry == null ? Optional.empty() : expiry;
        maxPainStrike = maxPainStrike == null ? Optional.empty() : maxPainStrike;
        curve = curve == null ? List.of() : List.copyOf(curve);
        nearestStrikes = nearestStrikes == null ? List.of() : List.copyOf(nearestStrikes);
        status = Objects.requireNonNull(status, "status must not be null");
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        summary = summary.trim();
    }
}
