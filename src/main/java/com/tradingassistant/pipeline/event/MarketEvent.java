package com.tradingassistant.pipeline.event;

import java.time.Instant;

/**
 * An immutable fact published by the market-processing pipeline.
 *
 * <p>Events carry domain objects only: no broker DTO, provider payload, HTTP
 * detail or mutable collection ever crosses this boundary. The sealed hierarchy
 * is deliberately small so a later phase can add a transport without changing
 * what an event means.</p>
 */
public sealed interface MarketEvent
        permits TickReceivedEvent, CandleCompletedEvent, IndicatorUpdatedEvent,
        SessionStartedEvent, SessionExpiredEvent, MarketAssessmentGeneratedEvent,
        OptionsAssessmentGeneratedEvent, StrategyEvaluatedEvent, TradeQualifiedEvent,
        RiskAssessmentGeneratedEvent, OrderCreatedEvent, OrderValidatedEvent,
        OrderSubmittedEvent, OrderAcceptedEvent, OrderRejectedEvent,
        OrderPartiallyFilledEvent, OrderFilledEvent, OrderCancelledEvent,
        PositionOpenedEvent, PositionModifiedEvent, PositionClosedEvent,
        PortfolioUpdatedEvent, TradeCompletedEvent {

    MarketEventType type();

    /**
     * Application time at which the event was created. This is not the market
     * timestamp of the underlying data, which travels inside the payload.
     */
    Instant occurredAt();
}
