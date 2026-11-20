package com.tradingassistant.platform.watchlist;

import com.tradingassistant.common.api.ErrorCode;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.platform.error.PlatformException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Account-scoped watchlist use cases.
 *
 * <p>Every lookup includes the owner identity, so an identifier from another
 * account is indistinguishable from a missing one.</p>
 */
@Service
public class WatchlistService {

    private static final int MAXIMUM_WATCHLISTS = 25;

    private final WatchlistRepository repository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public WatchlistService(WatchlistRepository repository,
                            ApplicationEventPublisher events,
                            Clock clock) {
        this.repository = repository;
        this.events = events;
        this.clock = clock;
    }

    public List<WatchlistDocument> list(String userId) {
        return repository.findByUserIdOrderByCreatedAtAsc(userId);
    }

    public WatchlistDocument require(String userId, String watchlistId) {
        return repository.findByIdAndUserId(watchlistId, userId)
                .orElseThrow(() -> notFound());
    }

    public WatchlistDocument create(String userId, String name) {
        String normalised = requireName(name);
        if (repository.countByUserId(userId) >= MAXIMUM_WATCHLISTS) {
            throw new PlatformException(ErrorCode.CONFLICT,
                    "An account may not have more than " + MAXIMUM_WATCHLISTS
                            + " watchlists.");
        }
        if (repository.existsByUserIdAndName(userId, normalised)) {
            throw duplicateName();
        }
        try {
            WatchlistDocument created = repository.save(WatchlistDocument.create(userId,
                    normalised, clock.instant()));
            publish(userId);
            return created;
        } catch (DuplicateKeyException race) {
            throw duplicateName();
        }
    }

    public WatchlistDocument rename(String userId, String watchlistId, String name) {
        WatchlistDocument watchlist = require(userId, watchlistId);
        String normalised = requireName(name);
        if (watchlist.name().equals(normalised)) {
            return watchlist;
        }
        if (repository.existsByUserIdAndName(userId, normalised)) {
            throw duplicateName();
        }
        WatchlistDocument renamed = repository.save(watchlist.rename(normalised,
                clock.instant()));
        publish(userId);
        return renamed;
    }

    public void delete(String userId, String watchlistId) {
        WatchlistDocument watchlist = require(userId, watchlistId);
        repository.delete(watchlist);
        publish(userId);
    }

    public WatchlistDocument addItem(String userId, String watchlistId,
                                     String symbolToken, String symbol,
                                     Segment segment) {
        WatchlistDocument watchlist = require(userId, watchlistId);
        String token = required(symbolToken, "symbolToken");
        String display = required(symbol, "symbol");
        if (segment == null) {
            throw new PlatformException(ErrorCode.VALIDATION_FAILED,
                    "segment must not be null.");
        }
        if (watchlist.contains(token, segment)) {
            return watchlist;
        }
        if (watchlist.items().size() >= WatchlistDocument.MAXIMUM_ITEMS) {
            throw new PlatformException(ErrorCode.CONFLICT,
                    "A watchlist may not hold more than "
                            + WatchlistDocument.MAXIMUM_ITEMS + " instruments.");
        }
        List<WatchlistItem> items = new ArrayList<>(watchlist.items());
        items.add(new WatchlistItem(UUID.randomUUID().toString(), token, display,
                segment, clock.instant()));
        WatchlistDocument updated = repository.save(watchlist.withItems(items,
                clock.instant()));
        publish(userId);
        return updated;
    }

    public WatchlistDocument removeItem(String userId, String watchlistId,
                                        String itemId) {
        WatchlistDocument watchlist = require(userId, watchlistId);
        List<WatchlistItem> remaining = watchlist.items().stream()
                .filter(item -> !item.itemId().equals(itemId))
                .toList();
        if (remaining.size() == watchlist.items().size()) {
            throw new PlatformException(ErrorCode.RESOURCE_NOT_FOUND,
                    "Watchlist item not found.");
        }
        WatchlistDocument updated = repository.save(watchlist.withItems(remaining,
                clock.instant()));
        publish(userId);
        return updated;
    }

    public long count(String userId) {
        return repository.countByUserId(userId);
    }

    private static String requireName(String name) {
        try {
            return WatchlistDocument.requireName(name);
        } catch (IllegalArgumentException failure) {
            throw new PlatformException(ErrorCode.VALIDATION_FAILED,
                    failure.getMessage());
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_FAILED,
                    field + " must not be blank.");
        }
        return value.trim();
    }

    private void publish(String userId) {
        events.publishEvent(new WatchlistChangedEvent(userId));
    }

    private static PlatformException duplicateName() {
        return new PlatformException(ErrorCode.CONFLICT,
                "A watchlist with this name already exists.");
    }

    private static PlatformException notFound() {
        return new PlatformException(ErrorCode.RESOURCE_NOT_FOUND,
                "Watchlist not found.");
    }
}
