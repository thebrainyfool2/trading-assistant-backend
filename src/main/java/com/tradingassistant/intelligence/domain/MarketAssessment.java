package com.tradingassistant.intelligence.domain;

import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable, broker-independent description of the market for one instrument
 * and interval at one completed candle.
 *
 * <p>The model describes; it never prescribes. It contains no score, no
 * direction to trade, no position size, no risk measure and no option data.
 * Each aspect is a separate nested assessment so a consumer never has to parse
 * a combined field.</p>
 *
 * @param instrument    instrument the assessment belongs to
 * @param interval      candle interval the assessment was produced on
 * @param timestamp     timestamp of the assessed candle
 * @param status        overall availability state of the assessment
 * @param warmup        true when any aspect is not fully ready
 * @param confidence    confidence metadata derived from ready aspects
 * @param trend         trend aspect
 * @param momentum      momentum aspect
 * @param volatility    volatility aspect
 * @param participation participation aspect
 * @param structure     market structure aspect
 * @param session       exchange session context
 */
public record MarketAssessment(
        InstrumentKey instrument,
        Interval interval,
        Instant timestamp,
        AssessmentStatus status,
        boolean warmup,
        Confidence confidence,
        TrendAssessment trend,
        MomentumAssessment momentum,
        VolatilityAssessment volatility,
        ParticipationAssessment participation,
        StructureAssessment structure,
        SessionAssessment session) {

    public MarketAssessment {
        instrument = Objects.requireNonNull(instrument, "instrument must not be null");
        interval = Objects.requireNonNull(interval, "interval must not be null");
        timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        confidence = Objects.requireNonNull(confidence, "confidence must not be null");
        trend = Objects.requireNonNull(trend, "trend must not be null");
        momentum = Objects.requireNonNull(momentum, "momentum must not be null");
        volatility = Objects.requireNonNull(volatility, "volatility must not be null");
        participation = Objects.requireNonNull(participation, "participation must not be null");
        structure = Objects.requireNonNull(structure, "structure must not be null");
        session = Objects.requireNonNull(session, "session must not be null");
    }

    /**
     * True when the market structure aspect reports a range.
     */
    public boolean ranging() {
        return structure.bias() == StructureBias.RANGING;
    }
}
