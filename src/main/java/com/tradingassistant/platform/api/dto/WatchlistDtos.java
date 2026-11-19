package com.tradingassistant.platform.api.dto;

import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.platform.watchlist.WatchlistDocument;
import com.tradingassistant.platform.watchlist.WatchlistItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Watchlist API contract.
 */
public final class WatchlistDtos {

    private WatchlistDtos() {
    }

    public record WatchlistItemView(
            String id,
            String symbolToken,
            String symbol,
            Segment segment,
            Instant addedAt) {

        public static WatchlistItemView from(WatchlistItem item) {
            return new WatchlistItemView(item.itemId(), item.symbolToken(),
                    item.symbol(), item.segment(), item.addedAt());
        }
    }

    public record WatchlistView(
            String id,
            String name,
            List<WatchlistItemView> items,
            Instant createdAt,
            Instant updatedAt) {

        public static WatchlistView from(WatchlistDocument watchlist) {
            return new WatchlistView(watchlist.id(), watchlist.name(),
                    watchlist.items().stream().map(WatchlistItemView::from).toList(),
                    watchlist.createdAt(), watchlist.updatedAt());
        }
    }

    public record CreateWatchlistRequest(@NotBlank @Size(max = 60) String name) {
    }

    public record RenameWatchlistRequest(@NotBlank @Size(max = 60) String name) {
    }

    public record AddItemRequest(
            @NotBlank @Size(max = 64) String symbolToken,
            @NotBlank @Size(max = 64) String symbol,
            @NotNull Segment segment) {
    }
}
