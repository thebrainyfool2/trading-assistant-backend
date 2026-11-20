package com.tradingassistant.platform.live;

import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.platform.watchlist.WatchlistDocument;
import com.tradingassistant.platform.watchlist.WatchlistItem;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Builds the deterministic live subscription set from configured instruments and
 * every watchlist entry.
 *
 * <p>Configured instruments are resolved first, then watchlists in their stored
 * order. Duplicate token/segment pairs collapse. Instruments whose exchange has
 * no documented market feed are reported and skipped rather than silently
 * subscribed. The result is capped so a large watchlist cannot create an
 * unbounded WebSocket request.</p>
 */
public final class LiveSubscriptionPlanner {

    private static final Set<Segment> FEED_SEGMENTS = Set.of(
            Segment.NSE_CASH,
            Segment.NSE_FO,
            Segment.BSE_CASH,
            Segment.BSE_FO,
            Segment.MCX_FO);

    private final LiveInstrumentResolver resolver;
    private final int maxInstruments;

    public LiveSubscriptionPlanner(LiveInstrumentResolver resolver, int maxInstruments) {
        this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
        if (maxInstruments < 1) {
            throw new IllegalArgumentException("maxInstruments must be positive");
        }
        this.maxInstruments = maxInstruments;
    }

    public Plan plan(String configured, List<WatchlistDocument> watchlists) {
        LinkedHashSet<InstrumentKey> selected = new LinkedHashSet<>();
        List<String> warnings = new ArrayList<>();

        for (String entry : split(configured)) {
            try {
                resolver.resolve(entry).ifPresentOrElse(selected::add,
                        () -> warnings.add("Could not resolve live instrument " + entry));
            } catch (RuntimeException failure) {
                warnings.add("Invalid live instrument " + entry + ": " + failure.getMessage());
            }
        }

        if (watchlists != null) {
            for (WatchlistDocument watchlist : watchlists) {
                if (watchlist == null) {
                    continue;
                }
                for (WatchlistItem item : watchlist.items()) {
                    if (!FEED_SEGMENTS.contains(item.segment())) {
                        warnings.add("Watchlist " + watchlist.name() + " item " + item.symbol()
                                + " uses a segment without a documented market feed: "
                                + item.segment().code());
                        continue;
                    }
                    selected.add(new InstrumentKey(item.segment(), item.symbolToken()));
                }
            }
        }

        List<InstrumentKey> bounded = selected.stream().limit(maxInstruments).toList();
        if (selected.size() > bounded.size()) {
            warnings.add("Live subscription capped at " + maxInstruments + " instruments; "
                    + (selected.size() - bounded.size()) + " ignored");
        }
        return new Plan(bounded, List.copyOf(warnings));
    }

    private static List<String> split(String configured) {
        if (configured == null || configured.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String entry : configured.split(",")) {
            if (!entry.isBlank()) {
                values.add(entry.trim());
            }
        }
        return List.copyOf(values);
    }

    /**
     * Immutable planner output. The list is ordered: configured instruments first,
     * then watchlists and their items.
     */
    public record Plan(List<InstrumentKey> instruments, List<String> warnings) {

        public Plan {
            instruments = instruments == null ? List.of() : List.copyOf(instruments);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }
}
