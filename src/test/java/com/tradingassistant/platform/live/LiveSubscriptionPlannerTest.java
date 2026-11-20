package com.tradingassistant.platform.live;

import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.market.service.InstrumentService;
import com.tradingassistant.platform.watchlist.WatchlistDocument;
import com.tradingassistant.platform.watchlist.WatchlistItem;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LiveSubscriptionPlannerTest {

    private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");
    private final InstrumentService instruments = mock(InstrumentService.class);
    private final LiveSubscriptionPlanner planner = new LiveSubscriptionPlanner(
            new LiveInstrumentResolver(instruments, Clock.fixed(NOW, ZoneOffset.UTC),
                    Duration.ofMinutes(10)), 10);

    @Test
    void combinesConfiguredInstrumentsAndWatchlistsWithoutDuplicates() {
        WatchlistDocument list = new WatchlistDocument("list-1", "local-owner", "Main",
                List.of(item("2885", Segment.NSE_CASH), item("1234", Segment.NSE_FO),
                        item("2885", Segment.NSE_CASH)),
                NOW, NOW);

        LiveSubscriptionPlanner.Plan plan = planner.plan("NSE_CASH:2885,NSE_CASH:2885",
                List.of(list));

        assertThat(plan.instruments()).containsExactly(
                new InstrumentKey(Segment.NSE_CASH, "2885"),
                new InstrumentKey(Segment.NSE_FO, "1234"));
        assertThat(plan.warnings()).isEmpty();
    }

    @Test
    void skipsSegmentsWithoutADocumentedFeed() {
        WatchlistDocument list = new WatchlistDocument("list-1", "local-owner", "Main",
                List.of(item("999", Segment.NSE_COM)), NOW, NOW);

        LiveSubscriptionPlanner.Plan plan = planner.plan("NSE_CASH:2885", List.of(list));

        assertThat(plan.instruments()).containsExactly(
                new InstrumentKey(Segment.NSE_CASH, "2885"));
        assertThat(plan.warnings()).singleElement()
                .asString().contains("NCO");
    }

    @Test
    void capsTheSubscriptionSetDeterministically() {
        LiveSubscriptionPlanner limited = new LiveSubscriptionPlanner(
                new LiveInstrumentResolver(instruments, Clock.fixed(NOW, ZoneOffset.UTC),
                        Duration.ofMinutes(10)), 2);

        WatchlistDocument list = new WatchlistDocument("list-1", "local-owner", "Main",
                List.of(item("1234", Segment.NSE_FO), item("5678", Segment.NSE_FO)),
                NOW, NOW);

        LiveSubscriptionPlanner.Plan plan = limited.plan("NSE_CASH:2885", List.of(list));

        assertThat(plan.instruments()).containsExactly(
                new InstrumentKey(Segment.NSE_CASH, "2885"),
                new InstrumentKey(Segment.NSE_FO, "1234"));
        assertThat(plan.warnings()).anySatisfy(warning ->
                assertThat(warning).contains("capped at 2"));
    }

    private static WatchlistItem item(String token, Segment segment) {
        return new WatchlistItem(token + "-item", token, "SYMBOL" + token, segment, NOW);
    }
}
