package com.tradingassistant.options.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Pain at one candidate settlement strike: the aggregate intrinsic value that
 * would be paid to option holders if the underlying settled there.
 *
 * @param strike   candidate settlement strike
 * @param callPain intrinsic value owed on calls
 * @param putPain  intrinsic value owed on puts
 * @param totalPain call pain plus put pain
 */
public record PainPoint(
        BigDecimal strike,
        BigDecimal callPain,
        BigDecimal putPain,
        BigDecimal totalPain) {

    public PainPoint {
        strike = Objects.requireNonNull(strike, "strike must not be null");
        callPain = Objects.requireNonNull(callPain, "callPain must not be null");
        putPain = Objects.requireNonNull(putPain, "putPain must not be null");
        totalPain = Objects.requireNonNull(totalPain, "totalPain must not be null");
        if (callPain.signum() < 0 || putPain.signum() < 0) {
            throw new IllegalArgumentException("pain must not be negative");
        }
    }
}
