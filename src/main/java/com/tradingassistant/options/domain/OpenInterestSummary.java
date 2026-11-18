package com.tradingassistant.options.domain;

import com.tradingassistant.intelligence.domain.AssessmentStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Descriptive open-interest summary of the chain.
 *
 * <p>Support and resistance zones are the strikes with the largest put and call
 * open interest respectively. They describe where open interest is concentrated;
 * they are not levels to trade.</p>
 */
public record OpenInterestSummary(
        long totalCallOpenInterest,
        long totalPutOpenInterest,
        long totalCallVolume,
        long totalPutVolume,
        List<StrikeOpenInterest> strikes,
        Optional<BigDecimal> callConcentrationStrike,
        Optional<BigDecimal> putConcentrationStrike,
        List<BigDecimal> supportZones,
        List<BigDecimal> resistanceZones,
        AssessmentStatus status,
        String summary) {

    public OpenInterestSummary {
        if (totalCallOpenInterest < 0 || totalPutOpenInterest < 0
                || totalCallVolume < 0 || totalPutVolume < 0) {
            throw new IllegalArgumentException("open interest and volume must not be negative");
        }
        strikes = strikes == null ? List.of() : List.copyOf(strikes);
        callConcentrationStrike = callConcentrationStrike == null ? Optional.empty()
                : callConcentrationStrike;
        putConcentrationStrike = putConcentrationStrike == null ? Optional.empty()
                : putConcentrationStrike;
        supportZones = supportZones == null ? List.of() : List.copyOf(supportZones);
        resistanceZones = resistanceZones == null ? List.of() : List.copyOf(resistanceZones);
        status = Objects.requireNonNull(status, "status must not be null");
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        summary = summary.trim();
    }

    public long totalOpenInterest() {
        return totalCallOpenInterest + totalPutOpenInterest;
    }
}
