package com.tradingassistant.market.domain;

import java.util.List;

/**
 * Best bid and ask ladders. Empty ladders are explicit rather than null.
 */
public record MarketDepth(List<DepthLevel> buy, List<DepthLevel> sell) {

    public MarketDepth {
        buy = copy(buy, "buy");
        sell = copy(sell, "sell");
    }

    public static MarketDepth empty() {
        return new MarketDepth(List.of(), List.of());
    }

    public boolean isEmpty() {
        return buy.isEmpty() && sell.isEmpty();
    }

    private static List<DepthLevel> copy(List<DepthLevel> levels, String side) {
        if (levels == null) {
            return List.of();
        }
        if (levels.stream().anyMatch(level -> level == null)) {
            throw new IllegalArgumentException(side + " depth must not contain null levels");
        }
        return List.copyOf(levels);
    }
}
