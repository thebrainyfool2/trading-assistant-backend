package com.tradingassistant.market.domain;

/**
 * An exchange whose instruments can enter the application's market domain.
 *
 * <p>Exchange codes are stable market identifiers, not broker product names.
 * Segment-specific routing is represented separately by {@link Segment}.</p>
 */
public enum Exchange {
    NSE,
    BSE,
    MCX,
    CDS,
    NCDEX;

    public String code() {
        return name();
    }
}
