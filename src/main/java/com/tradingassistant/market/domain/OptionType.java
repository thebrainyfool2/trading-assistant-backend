package com.tradingassistant.market.domain;

public enum OptionType {
    CALL,
    PUT;

    public boolean isCall() {
        return this == CALL;
    }

    public boolean isPut() {
        return this == PUT;
    }
}
