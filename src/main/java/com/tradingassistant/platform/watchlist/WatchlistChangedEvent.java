package com.tradingassistant.platform.watchlist;

/**
 * Published after a watchlist mutation so the live subscription supervisor can
 * reconcile the feed without polling the database on every request.
 *
 * @param ownerId owner whose watchlists changed
 */
public record WatchlistChangedEvent(String ownerId) {

    public WatchlistChangedEvent {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
        ownerId = ownerId.trim();
    }
}
