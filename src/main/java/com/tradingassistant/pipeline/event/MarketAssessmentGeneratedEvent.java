package com.tradingassistant.pipeline.event;

import com.tradingassistant.intelligence.domain.MarketAssessment;

import java.time.Instant;
import java.util.Objects;

/**
 * A market assessment was produced for one completed candle.
 *
 * <p>The event lives in the shared event package because a sealed interface in
 * the unnamed module requires its permitted subtypes in the same package. Its
 * payload is the immutable {@link MarketAssessment} and nothing else: no
 * analyzer, configuration or pipeline object is published, and the event package
 * gains no behavioural dependency on the intelligence layer.</p>
 *
 * @param assessment immutable description of the market
 * @param occurredAt application time of publication
 */
public record MarketAssessmentGeneratedEvent(
        MarketAssessment assessment,
        Instant occurredAt) implements MarketEvent {

    public MarketAssessmentGeneratedEvent {
        assessment = Objects.requireNonNull(assessment, "assessment must not be null");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    @Override
    public MarketEventType type() {
        return MarketEventType.MARKET_ASSESSMENT_GENERATED;
    }
}
