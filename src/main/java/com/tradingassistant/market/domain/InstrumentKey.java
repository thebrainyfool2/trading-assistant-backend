package com.tradingassistant.market.domain;

/**
 * Identity of an instrument in the application domain. A token is unique within
 * a market segment, not globally, so the segment is part of the key.
 */
public record InstrumentKey(Segment segment, String symbolToken) {

    public InstrumentKey {
        segment = DomainValidation.required(segment, "segment");
        symbolToken = DomainValidation.text(symbolToken, "symbolToken");
    }

    public Exchange exchange() {
        return segment.exchange();
    }
}
