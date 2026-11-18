package com.tradingassistant.pipeline.aggregation;

/**
 * Result of offering one tick to an aggregator. Every rejected tick has exactly
 * one reason, and the reason is a pure function of the tick, the aggregator
 * state and the clock.
 */
public enum AggregationStatus {

    /** The tick was merged into the forming candle. */
    ACCEPTED,

    /** The tick belongs to a different instrument than the aggregator. */
    IGNORED_WRONG_INSTRUMENT,

    /** The tick carries no last traded price, so no OHLCV value can be derived. */
    IGNORED_MISSING_PRICE,

    /** The tick sequence number is not greater than the last accepted sequence. */
    IGNORED_SEQUENCE,

    /** The tick timestamp is earlier than the forming candle, so it is late. */
    IGNORED_LATE_TIMESTAMP,

    /** The tick timestamp is further ahead of the clock than the policy allows. */
    IGNORED_FUTURE_TIMESTAMP,

    /** The tick timestamp is older than the policy allows and staleness checking is enabled. */
    IGNORED_STALE_TIMESTAMP,

    /** The tick timestamp is outside the calendar trading session. */
    IGNORED_OUTSIDE_SESSION
}
