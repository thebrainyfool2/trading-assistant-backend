package com.tradingassistant.options.domain;

import com.tradingassistant.intelligence.domain.AssessmentStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Put/call ratios by open interest and by volume, either for the whole chain,
 * per expiry, or both, as requested by configuration.
 */
public record PutCallRatioSummary(
        Optional<BigDecimal> chainOpenInterestRatio,
        Optional<BigDecimal> chainVolumeRatio,
        List<ExpiryPutCallRatio> expiries,
        PcrAggregation aggregation,
        AssessmentStatus status,
        String summary) {

    public PutCallRatioSummary {
        chainOpenInterestRatio = chainOpenInterestRatio == null ? Optional.empty()
                : chainOpenInterestRatio;
        chainVolumeRatio = chainVolumeRatio == null ? Optional.empty() : chainVolumeRatio;
        expiries = expiries == null ? List.of() : List.copyOf(expiries);
        aggregation = Objects.requireNonNull(aggregation, "aggregation must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        summary = summary.trim();
    }
}
