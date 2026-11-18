package com.tradingassistant.broker.model;

/**
 * Documented SmartAPI candle intervals and the maximum date span accepted in one
 * request.
 */
public enum CandleInterval {
    ONE_MINUTE("ONE_MINUTE", 30),
    THREE_MINUTE("THREE_MINUTE", 60),
    FIVE_MINUTE("FIVE_MINUTE", 100),
    TEN_MINUTE("TEN_MINUTE", 100),
    FIFTEEN_MINUTE("FIFTEEN_MINUTE", 200),
    THIRTY_MINUTE("THIRTY_MINUTE", 200),
    ONE_HOUR("ONE_HOUR", 400),
    ONE_DAY("ONE_DAY", 2000);

    private final String code;
    private final int maxDays;

    CandleInterval(String code, int maxDays) {
        this.code = code;
        this.maxDays = maxDays;
    }

    public String code() {
        return code;
    }

    public int maxDays() {
        return maxDays;
    }
}
