package com.tradingassistant.platform.watchlist;

import com.tradingassistant.market.domain.Segment;

import java.time.Instant;
import java.util.Objects;

/**
 * One instrument followed by a watchlist.
 *
 * @param itemId      stable item identity inside the watchlist
 * @param symbolToken broker instrument token
 * @param symbol      display symbol
 * @param segment     market segment
 * @param addedAt     instant the instrument was added
 */
public record WatchlistItem(
        String itemId,
        String symbolToken,
        String symbol,
        Segment segment,
        Instant addedAt) {

    public WatchlistItem {
        itemId = requireText(itemId, "itemId");
        symbolToken = requireText(symbolToken, "symbolToken");
        symbol = requireText(symbol, "symbol").toUpperCase(java.util.Locale.ROOT);
        segment = Objects.requireNonNull(segment, "segment must not be null");
        addedAt = Objects.requireNonNull(addedAt, "addedAt must not be null");
    }

    public boolean matches(String token, Segment marketSegment) {
        return symbolToken.equals(token == null ? null : token.trim())
                && segment == marketSegment;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
