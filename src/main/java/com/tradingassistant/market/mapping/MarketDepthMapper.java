package com.tradingassistant.market.mapping;

import com.tradingassistant.market.domain.DepthLevel;
import com.tradingassistant.market.domain.MarketDepth;
import com.tradingassistant.market.exception.MarketDataMappingException;

import java.util.List;

/**
 * Normalizes provider depth, preserving optional quantity/order fields and
 * rejecting null levels rather than emitting a partially corrupt ladder.
 */
public final class MarketDepthMapper {

    public MarketDepth toDomain(com.tradingassistant.broker.model.MarketDepth source) {
        if (source == null) {
            return MarketDepth.empty();
        }
        try {
            return new MarketDepth(levels(source.buy()), levels(source.sell()));
        } catch (MarketDataMappingException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MarketDataMappingException("Market depth could not be mapped.", exception);
        }
    }

    private static List<DepthLevel> levels(List<com.tradingassistant.broker.model.DepthLevel> levels) {
        if (levels == null) {
            return List.of();
        }
        return levels.stream()
                .map(level -> {
                    if (level == null) {
                        throw new MarketDataMappingException("A market-depth level was null.");
                    }
                    return new DepthLevel(level.price(), level.quantity(), level.orders());
                })
                .toList();
    }
}
