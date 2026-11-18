package com.tradingassistant.pipeline.event;

import com.tradingassistant.risk.domain.RiskAssessment;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Risk boundaries were assessed for qualified trades of one instrument and
 * instant.
 *
 * <p>The event lives in the shared event package because a sealed interface in the
 * unnamed module requires its permitted subtypes in the same package. Its payload
 * is an immutable list of immutable assessments; no rule, registry, calculator or
 * engine object is published, and the event carries no approval, recommendation,
 * order or position instruction.</p>
 *
 * @param assessments outcomes in definite strategy order
 * @param occurredAt  application time of publication
 */
public record RiskAssessmentGeneratedEvent(
        List<RiskAssessment> assessments,
        Instant occurredAt) implements MarketEvent {

    public RiskAssessmentGeneratedEvent {
        if (assessments == null || assessments.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("assessments must not contain null entries");
        }
        assessments = List.copyOf(assessments);
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    @Override
    public MarketEventType type() {
        return MarketEventType.RISK_ASSESSMENT_GENERATED;
    }
}
