package com.tradingassistant.qualification.domain;

import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable outcome of qualifying one strategy evaluation.
 *
 * <p>The model deliberately contains no recommendation, ranking, score,
 * probability, position, quantity, capital, risk measure or order. It states only
 * whether the strategy evaluation is currently eligible for downstream
 * consideration, together with the rule outcomes that led to that answer.</p>
 *
 * @param strategyId      identity of the qualified strategy
 * @param strategyName    name of the qualified strategy
 * @param instrument      instrument the strategy was evaluated on
 * @param interval        candle interval of the strategy evaluation
 * @param timestamp       market timestamp that was qualified
 * @param status          aggregate outcome of the rule evaluations
 * @param ruleEvaluations individual rule outcomes in registry order
 * @param summary         short deterministic explanation
 */
public record TradeQualification(
        String strategyId,
        String strategyName,
        InstrumentKey instrument,
        Interval interval,
        Instant timestamp,
        QualificationStatus status,
        List<QualificationRuleEvaluation> ruleEvaluations,
        String summary) {

    public TradeQualification {
        if (strategyId == null || strategyId.isBlank()) {
            throw new IllegalArgumentException("strategyId must not be blank");
        }
        if (strategyName == null || strategyName.isBlank()) {
            throw new IllegalArgumentException("strategyName must not be blank");
        }
        instrument = Objects.requireNonNull(instrument, "instrument must not be null");
        interval = Objects.requireNonNull(interval, "interval must not be null");
        timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        if (ruleEvaluations == null || ruleEvaluations.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("ruleEvaluations must not contain null entries");
        }
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        ruleEvaluations = List.copyOf(ruleEvaluations);
        strategyId = strategyId.trim();
        strategyName = strategyName.trim();
        summary = summary.trim();
    }

    /**
     * True only when every qualification rule passed.
     */
    public boolean qualified() {
        return status == QualificationStatus.QUALIFIED;
    }

    public int ruleCount() {
        return ruleEvaluations.size();
    }

    /**
     * Outcome recorded for one rule, when that rule took part.
     */
    public java.util.Optional<QualificationRuleEvaluation> rule(String ruleId) {
        if (ruleId == null || ruleId.isBlank()) {
            return java.util.Optional.empty();
        }
        String trimmed = ruleId.trim();
        return ruleEvaluations.stream()
                .filter(evaluation -> evaluation.ruleId().equals(trimmed))
                .findFirst();
    }
}
