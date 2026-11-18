package com.tradingassistant.intelligence.domain;

/**
 * Observable structure facts. Each signal is a statement about price geometry,
 * never an instruction.
 */
public enum StructureSignal {

    /** The recent window printed a high above the previous window's high. */
    HIGHER_HIGH,

    /** The recent window printed a low above the previous window's low. */
    HIGHER_LOW,

    /** The recent window printed a high below the previous window's high. */
    LOWER_HIGH,

    /** The recent window printed a low below the previous window's low. */
    LOWER_LOW,

    /** Both highs and lows stayed within the configured tolerance. */
    RANGE,

    /** The latest close is above every earlier high in the window. */
    BREAKOUT_CANDIDATE,

    /** The latest close is below every earlier low in the window. */
    BREAKDOWN_CANDIDATE
}
