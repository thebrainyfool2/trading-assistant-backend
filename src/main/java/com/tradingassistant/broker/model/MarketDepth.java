package com.tradingassistant.broker.model;

import java.util.List;

public record MarketDepth(List<DepthLevel> buy, List<DepthLevel> sell) {

    public MarketDepth {
        buy = buy == null ? List.of() : List.copyOf(buy);
        sell = sell == null ? List.of() : List.copyOf(sell);
    }
}
