package com.tradingassistant.qualification.domain;

import com.tradingassistant.market.domain.InstrumentKey;

import java.time.Instant;
import java.util.Objects;

/**
 * Identity of one qualification attempt: the strategy, the instrument and the
 * market instant it was evaluated for.
 *
 * <p>The key exists so duplicate qualification of the same candle can be detected
 * without comparing whole evaluations. It contains no outcome and no state.</p>
 *
 * @param strategyId strategy identity
 * @param instrument instrument the strategy was evaluated on
 * @param timestamp  market timestamp of the evaluation
 */
public record QualificationKey(
        String strategyId,
        InstrumentKey instrument,
        Instant timestamp) {

    public QualificationKey {
        if (strategyId == null || strategyId.isBlank()) {
            throw new IllegalArgumentException("strategyId must not be blank");
        }
        instrument = Objects.requireNonNull(instrument, "instrument must not be null");
        timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        strategyId = strategyId.trim();
    }

    /**
     * Key of the strategy evaluation carried by a context.
     */
    public static QualificationKey of(TradeQualificationContext context) {
        Objects.requireNonNull(context, "context must not be null");
        return new QualificationKey(context.strategyEvaluation().strategyId(),
                context.strategyEvaluation().instrument(),
                context.strategyEvaluation().timestamp());
    }
}
