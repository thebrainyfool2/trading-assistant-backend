package com.tradingassistant.platform.watchlist;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A named collection of instruments owned by one account.
 *
 * <p>Watchlists are account-private. The unique compound index on owner and name
 * is case-sensitive at the database level; the service normalises the name so
 * two lists differing only in case cannot coexist.</p>
 */
@Document(collection = "watchlists")
@CompoundIndex(name = "watchlist_owner_name", def = "{'userId': 1, 'name': 1}",
        unique = true)
public record WatchlistDocument(
        @Id String id,
        @Indexed String userId,
        String name,
        List<WatchlistItem> items,
        Instant createdAt,
        Instant updatedAt) {

    public static final int MAXIMUM_ITEMS = 100;

    public WatchlistDocument {
        userId = Objects.requireNonNull(userId, "userId must not be null");
        name = requireName(name);
        items = items == null ? List.of() : List.copyOf(items);
        if (items.size() > MAXIMUM_ITEMS) {
            throw new IllegalArgumentException("A watchlist may not hold more than "
                    + MAXIMUM_ITEMS + " instruments");
        }
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public static WatchlistDocument create(String userId, String name, Instant at) {
        return new WatchlistDocument(null, userId, name, List.of(), at, at);
    }

    public WatchlistDocument rename(String newName, Instant at) {
        return new WatchlistDocument(id, userId, newName, items, createdAt, at);
    }

    public WatchlistDocument withItems(List<WatchlistItem> newItems, Instant at) {
        return new WatchlistDocument(id, userId, name, newItems, createdAt, at);
    }

    public boolean contains(String symbolToken, com.tradingassistant.market.domain.Segment segment) {
        return items.stream().anyMatch(item -> item.matches(symbolToken, segment));
    }

    public static String requireName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        String trimmed = value.trim();
        if (trimmed.length() > 60) {
            throw new IllegalArgumentException("name must not exceed 60 characters");
        }
        return trimmed;
    }
}
