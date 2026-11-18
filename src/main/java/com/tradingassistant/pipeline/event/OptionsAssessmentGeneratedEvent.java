package com.tradingassistant.pipeline.event;

import com.tradingassistant.options.domain.OptionsAssessment;

import java.time.Instant;
import java.util.Objects;

/**
 * An options assessment was produced for one underlying.
 *
 * <p>The event lives in the shared event package because a sealed interface in
 * the unnamed module requires its permitted subtypes in the same package. Its
 * payload is the immutable {@link OptionsAssessment} and nothing else: no chain,
 * quote, analyzer or configuration object is published, and the event package
 * gains no behavioural dependency on the options layer.</p>
 *
 * @param assessment immutable description of the derivatives market
 * @param occurredAt application time of publication
 */
public record OptionsAssessmentGeneratedEvent(
        OptionsAssessment assessment,
        Instant occurredAt) implements MarketEvent {

    public OptionsAssessmentGeneratedEvent {
        assessment = Objects.requireNonNull(assessment, "assessment must not be null");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    @Override
    public MarketEventType type() {
        return MarketEventType.OPTIONS_ASSESSMENT_GENERATED;
    }
}
