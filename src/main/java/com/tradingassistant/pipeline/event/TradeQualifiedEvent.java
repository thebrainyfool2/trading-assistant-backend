package com.tradingassistant.pipeline.event;

import com.tradingassistant.qualification.domain.TradeQualification;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Strategy evaluations were qualified for one instrument and instant.
 *
 * <p>The event lives in the shared event package because a sealed interface in the
 * unnamed module requires its permitted subtypes in the same package. Its payload
 * is an immutable list of immutable qualifications; no rule, registry, ledger or
 * engine object is published, and the event carries no recommendation, ranking,
 * order or position.</p>
 *
 * @param qualifications outcomes in definite strategy order
 * @param occurredAt     application time of publication
 */
public record TradeQualifiedEvent(
        List<TradeQualification> qualifications,
        Instant occurredAt) implements MarketEvent {

    public TradeQualifiedEvent {
        if (qualifications == null || qualifications.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("qualifications must not contain null entries");
        }
        qualifications = List.copyOf(qualifications);
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    @Override
    public MarketEventType type() {
        return MarketEventType.TRADE_QUALIFIED;
    }
}
