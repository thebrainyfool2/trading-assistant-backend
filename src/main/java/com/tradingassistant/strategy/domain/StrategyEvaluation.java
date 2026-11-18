package com.tradingassistant.strategy.domain;

import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable result of evaluating one strategy definition.
 *
 * <p>The model deliberately contains no recommendation, score, ranking,
 * probability, expectancy, position, quantity, confidence percentage, entry,
 * stop or target. It states only whether the strategy's defined conditions were
 * satisfied at one instrument and instant.</p>
 *
 * @param strategyId   definition identity
 * @param strategyName definition name
 * @param timestamp    market timestamp that was evaluated
 * @param instrument   instrument the conditions were evaluated on
 * @param interval     candle interval of the assessment
 * @param status       aggregate outcome of the rule evaluations
 * @param ruleEvaluations individual rule outcomes in definition order
 * @param summary      short deterministic explanation
 */
public record StrategyEvaluation(
        String strategyId,
        String strategyName,
        Instant timestamp,
        InstrumentKey instrument,
        Interval interval,
        EvaluationStatus status,
        List<RuleEvaluation> ruleEvaluations,
        String summary) {

    public StrategyEvaluation {
        if (strategyId == null || strategyId.isBlank()) {
            throw new IllegalArgumentException("strategyId must not be blank");
        }
        if (strategyName == null || strategyName.isBlank()) {
            throw new IllegalArgumentException("strategyName must not be blank");
        }
        timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        instrument = Objects.requireNonNull(instrument, "instrument must not be null");
        interval = Objects.requireNonNull(interval, "interval must not be null");
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

    public boolean passed() {
        return status == EvaluationStatus.PASSED;
    }

    public int ruleCount() {
        return ruleEvaluations.size();
    }
}
