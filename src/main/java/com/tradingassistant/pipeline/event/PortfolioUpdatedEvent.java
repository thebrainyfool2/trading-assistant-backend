package com.tradingassistant.pipeline.event;

import com.tradingassistant.execution.domain.PortfolioSnapshot;

import java.time.Instant;
import java.util.Objects;

/**
 * Portfolio state changed.
 *
 * @param portfolio  immutable portfolio snapshot
 * @param occurredAt application time of publication
 */
public record PortfolioUpdatedEvent(PortfolioSnapshot portfolio, Instant occurredAt)
        implements MarketEvent {

    public PortfolioUpdatedEvent {
        portfolio = Objects.requireNonNull(portfolio, "portfolio must not be null");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    @Override
    public MarketEventType type() {
        return MarketEventType.PORTFOLIO_UPDATED;
    }
}
