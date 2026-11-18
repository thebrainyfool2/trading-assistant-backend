package com.tradingassistant.broker.model;

/**
 * Exchanges supported by the broker. The numeric feed type is the value used by
 * the SmartAPI WebSocket protocol; only the documented feed exchanges are
 * enabled for streaming.
 */
public enum Exchange {
    NSE("NSE", 1, true),
    NFO("NFO", 2, true),
    BSE("BSE", 3, true),
    BFO("BFO", 4, true),
    MCX("MCX", 5, true),
    CDS("CDS", 13, false),
    NCDEX("NCDEX", 7, false);

    private final String code;
    private final int feedType;
    private final boolean feedSupported;

    Exchange(String code, int feedType, boolean feedSupported) {
        this.code = code;
        this.feedType = feedType;
        this.feedSupported = feedSupported;
    }

    public String code() {
        return code;
    }

    public int feedType() {
        if (!feedSupported) {
            throw new IllegalArgumentException("Exchange " + code + " is not supported by the documented market feed.");
        }
        return feedType;
    }

    public boolean supportsMarketFeed() {
        return feedSupported;
    }

    public static Exchange fromFeedType(int feedType) {
        for (Exchange exchange : values()) {
            if (exchange.feedSupported && exchange.feedType == feedType) {
                return exchange;
            }
        }
        throw new IllegalArgumentException("Unknown market-feed exchange type: " + feedType);
    }
}
