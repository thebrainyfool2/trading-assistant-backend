package com.tradingassistant.options.domain;

import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.intelligence.domain.SessionAssessment;
import com.tradingassistant.market.domain.InstrumentKey;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable description of the derivatives market for one underlying at one
 * instant.
 *
 * <p>Every section is a separate immutable summary. There is no score, no
 * rating, no trade direction and no recommendation anywhere in the model: the
 * assessment states what the chain, the volatility surface, the Greeks, the open
 * interest, the ratios, the expected move, the liquidity and the max-pain curve
 * currently look like.</p>
 */
public record OptionsAssessment(
        InstrumentKey spotInstrument,
        String underlying,
        Instant timestamp,
        AssessmentStatus status,
        boolean warmup,
        ChainSummary chain,
        VolatilitySummary volatility,
        GreeksSummary greeks,
        OpenInterestSummary openInterest,
        PutCallRatioSummary putCallRatio,
        ExpectedMoveSummary expectedMove,
        LiquiditySummary liquidity,
        MaxPainSummary maxPain,
        SessionAssessment session,
        Map<String, String> diagnostics) {

    public OptionsAssessment {
        spotInstrument = Objects.requireNonNull(spotInstrument, "spotInstrument must not be null");
        if (underlying == null || underlying.isBlank()) {
            throw new IllegalArgumentException("underlying must not be blank");
        }
        timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        chain = Objects.requireNonNull(chain, "chain must not be null");
        volatility = Objects.requireNonNull(volatility, "volatility must not be null");
        greeks = Objects.requireNonNull(greeks, "greeks must not be null");
        openInterest = Objects.requireNonNull(openInterest, "openInterest must not be null");
        putCallRatio = Objects.requireNonNull(putCallRatio, "putCallRatio must not be null");
        expectedMove = Objects.requireNonNull(expectedMove, "expectedMove must not be null");
        liquidity = Objects.requireNonNull(liquidity, "liquidity must not be null");
        maxPain = Objects.requireNonNull(maxPain, "maxPain must not be null");
        session = Objects.requireNonNull(session, "session must not be null");
        diagnostics = diagnostics == null ? Map.of() : Map.copyOf(diagnostics);
        underlying = underlying.trim().toUpperCase(java.util.Locale.ROOT);
        if (warmup != (status != AssessmentStatus.READY)) {
            throw new IllegalArgumentException("warmup must be true exactly when status is not READY");
        }
    }
}
