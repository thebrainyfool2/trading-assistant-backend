package com.tradingassistant.options.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Open interest, volume and positioning description for one expiry and strike.
 *
 * <p>Changes are empty when no earlier capture is available. Build-up is
 * classified from the leg's own premium change and open-interest change, so it
 * describes positioning in that contract rather than a view on the underlying.</p>
 */
public record StrikeOpenInterest(
        LocalDate expiry,
        BigDecimal strike,
        long callOpenInterest,
        long putOpenInterest,
        Optional<Long> callOpenInterestChange,
        Optional<Long> putOpenInterestChange,
        long callVolume,
        long putVolume,
        Optional<Long> callVolumeChange,
        Optional<Long> putVolumeChange,
        OpenInterestBuildUp callBuildUp,
        OpenInterestBuildUp putBuildUp) {

    public StrikeOpenInterest {
        expiry = Objects.requireNonNull(expiry, "expiry must not be null");
        strike = Objects.requireNonNull(strike, "strike must not be null");
        if (callOpenInterest < 0 || putOpenInterest < 0 || callVolume < 0 || putVolume < 0) {
            throw new IllegalArgumentException("open interest and volume must not be negative");
        }
        callOpenInterestChange = callOpenInterestChange == null ? Optional.empty()
                : callOpenInterestChange;
        putOpenInterestChange = putOpenInterestChange == null ? Optional.empty()
                : putOpenInterestChange;
        callVolumeChange = callVolumeChange == null ? Optional.empty() : callVolumeChange;
        putVolumeChange = putVolumeChange == null ? Optional.empty() : putVolumeChange;
        callBuildUp = Objects.requireNonNull(callBuildUp, "callBuildUp must not be null");
        putBuildUp = Objects.requireNonNull(putBuildUp, "putBuildUp must not be null");
    }

    public long totalOpenInterest() {
        return callOpenInterest + putOpenInterest;
    }
}
