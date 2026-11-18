package com.tradingassistant.options.domain;

import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.market.domain.InstrumentKey;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable capture of one chain: the structural chain plus the spot reference,
 * capture instant and diagnostics that describe how complete the capture was.
 *
 * @param chain      structural chain
 * @param capturedAt capture instant in UTC
 * @param spotPrice  underlying price used for moneyness and valuation
 * @param status     availability of the snapshot
 * @param metadata   deterministic diagnostics such as excluded contract counts
 */
public record ChainSnapshot(
        OptionChain chain,
        Instant capturedAt,
        BigDecimal spotPrice,
        AssessmentStatus status,
        Map<String, String> metadata) {

    public ChainSnapshot {
        chain = Objects.requireNonNull(chain, "chain must not be null");
        capturedAt = Objects.requireNonNull(capturedAt, "capturedAt must not be null");
        spotPrice = Objects.requireNonNull(spotPrice, "spotPrice must not be null");
        if (spotPrice.signum() <= 0) {
            throw new IllegalArgumentException("spotPrice must be positive");
        }
        status = Objects.requireNonNull(status, "status must not be null");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public InstrumentKey spotInstrument() {
        return chain.spotInstrument();
    }

    public String underlying() {
        return chain.underlying();
    }

    public List<ExpiryChain> expiries() {
        return chain.expiries();
    }

    public Optional<ExpiryChain> nearestExpiry() {
        return chain.nearestExpiry();
    }

    public Optional<LocalDate> nearestExpiryDate() {
        return chain.nearestExpiryDate();
    }

    public List<Strike> legs() {
        return chain.legs();
    }
}
