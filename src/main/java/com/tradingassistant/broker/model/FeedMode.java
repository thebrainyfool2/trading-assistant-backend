package com.tradingassistant.broker.model;

/**
 * Documented SmartAPI Streaming 2.0 modes. Depth mode is not included because it
 * is absent from the current official protocol page.
 */
public enum FeedMode {
    LTP(1),
    QUOTE(2),
    SNAP_QUOTE(3);

    private final int protocolValue;

    FeedMode(int protocolValue) {
        this.protocolValue = protocolValue;
    }

    public int protocolValue() {
        return protocolValue;
    }

    public static FeedMode fromProtocolValue(int value) {
        for (FeedMode mode : values()) {
            if (mode.protocolValue == value) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown market-feed mode: " + value);
    }
}
