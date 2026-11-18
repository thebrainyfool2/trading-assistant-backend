package com.tradingassistant.market.domain;

import java.util.Locale;

/**
 * A tradable segment on an exchange. The external code is the stable segment
 * identifier used by Indian market-data feeds (for example {@code NFO}); it is
 * not a SmartAPI DTO or response field.
 */
public enum Segment {
    NSE_CASH(Exchange.NSE, "NSE", false),
    NSE_FO(Exchange.NSE, "NFO", true),
    BSE_CASH(Exchange.BSE, "BSE", false),
    BSE_FO(Exchange.BSE, "BFO", true),
    MCX_FO(Exchange.MCX, "MCX", true),
    NSE_COM(Exchange.NSE, "NCO", true),
    CDS(Exchange.CDS, "CDS", true),
    NCDEX(Exchange.NCDEX, "NCDEX", true);

    private final Exchange exchange;
    private final String code;
    private final boolean derivative;

    Segment(Exchange exchange, String code, boolean derivative) {
        this.exchange = exchange;
        this.code = code;
        this.derivative = derivative;
    }

    public Exchange exchange() {
        return exchange;
    }

    public String code() {
        return code;
    }

    public boolean isDerivative() {
        return derivative;
    }

    /**
     * Resolves a stable market segment code. Unknown codes are rejected rather
     * than silently mapped to a cash segment.
     */
    public static Segment fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Segment code must not be blank.");
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        for (Segment segment : values()) {
            if (segment.code.equals(normalized) || segment.name().equals(normalized)) {
                return segment;
            }
        }
        throw new IllegalArgumentException("Unknown market segment: " + code);
    }
}
