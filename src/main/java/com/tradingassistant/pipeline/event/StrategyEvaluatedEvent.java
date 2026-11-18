package com.tradingassistant.pipeline.event;

import com.tradingassistant.strategy.domain.StrategyEvaluation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Strategies were evaluated for one instrument and instant.
 *
 * <p>The event lives in the shared event package because a sealed interface in
 * the unnamed module requires its permitted subtypes in the same package. Its
 * payload is an immutable list of immutable strategy evaluations; no definition,
 * rule, registry or engine object is published.</p>
 *
 * @param evaluations outcomes in definite strategy order
 * @param occurredAt  application time of publication
 */
public record StrategyEvaluatedEvent(
        List<StrategyEvaluation> evaluations,
        Instant occurredAt) implements MarketEvent {

    public StrategyEvaluatedEvent {
        if (evaluations == null || evaluations.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("evaluations must not contain null entries");
        }
        evaluations = List.copyOf(evaluations);
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    @Override
    public MarketEventType type() {
        return MarketEventType.STRATEGY_EVALUATED;
    }
}
