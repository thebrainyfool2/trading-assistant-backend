package com.tradingassistant.options.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Put/call ratios of one expiry. A ratio is empty when the call denominator is
 * zero, because a ratio against nothing is not a description.
 */
public record ExpiryPutCallRatio(
        LocalDate expiry,
        Optional<BigDecimal> openInterestRatio,
        Optional<BigDecimal> volumeRatio) {

    public ExpiryPutCallRatio {
        expiry = Objects.requireNonNull(expiry, "expiry must not be null");
        openInterestRatio = openInterestRatio == null ? Optional.empty() : openInterestRatio;
        volumeRatio = volumeRatio == null ? Optional.empty() : volumeRatio;
    }
}
