package com.tradingassistant.platform.watchlist;

import com.tradingassistant.common.api.ErrorCode;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.platform.PlatformTestFixtures;
import com.tradingassistant.platform.error.PlatformException;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WatchlistServiceTest {

    private final WatchlistRepository repository = mock(WatchlistRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final WatchlistService service = new WatchlistService(repository, events,
            Clock.fixed(PlatformTestFixtures.NOW, java.time.ZoneOffset.UTC));

    @Test
    void createPersistsAnEmptyList() {
        when(repository.existsByUserIdAndName("user-1", "Momentum")).thenReturn(false);
        when(repository.save(any(WatchlistDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WatchlistDocument created = service.create("user-1", "  Momentum ");

        assertThat(created.name()).isEqualTo("Momentum");
        assertThat(created.items()).isEmpty();
        verify(events).publishEvent(new WatchlistChangedEvent("user-1"));
    }

    @Test
    void createRejectsDuplicateName() {
        when(repository.existsByUserIdAndName("user-1", "Momentum")).thenReturn(true);

        assertThatThrownBy(() -> service.create("user-1", "Momentum"))
                .isInstanceOf(PlatformException.class)
                .extracting(failure -> ((PlatformException) failure).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void createRejectsMoreThanTheMaximumNumberOfLists() {
        when(repository.countByUserId("user-1")).thenReturn(25L);

        assertThatThrownBy(() -> service.create("user-1", "Extra"))
                .isInstanceOf(PlatformException.class)
                .extracting(failure -> ((PlatformException) failure).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsBlankName() {
        assertThatThrownBy(() -> service.create("user-1", " "))
                .isInstanceOf(PlatformException.class)
                .extracting(failure -> ((PlatformException) failure).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void lookupIsScopedByOwner() {
        when(repository.findByIdAndUserId("missing", "user-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.require("user-1", "missing"))
                .isInstanceOf(PlatformException.class)
                .extracting(failure -> ((PlatformException) failure).errorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void renameKeepsItemsAndRejectsDuplicateTargetName() {
        WatchlistDocument watchlist = WatchlistDocument.create("user-1", "Old",
                PlatformTestFixtures.NOW);
        when(repository.findByIdAndUserId("list-1", "user-1"))
                .thenReturn(Optional.of(watchlist));
        when(repository.existsByUserIdAndName("user-1", "Taken")).thenReturn(true);

        assertThatThrownBy(() -> service.rename("user-1", "list-1", "Taken"))
                .isInstanceOf(PlatformException.class)
                .extracting(failure -> ((PlatformException) failure).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void addingTheSameInstrumentIsIdempotent() {
        WatchlistDocument watchlist = withItems(List.of(
                new WatchlistItem("item-1", "2885", "RELIANCE", Segment.NSE_CASH,
                        PlatformTestFixtures.NOW)));
        when(repository.findByIdAndUserId("list-1", "user-1"))
                .thenReturn(Optional.of(watchlist));

        WatchlistDocument result = service.addItem("user-1", "list-1", "2885",
                "RELIANCE", Segment.NSE_CASH);

        assertThat(result.items()).hasSize(1);
        verify(repository, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void addingAnInstrumentAppendsAnItem() {
        WatchlistDocument watchlist = WatchlistDocument.create("user-1", "List",
                PlatformTestFixtures.NOW);
        when(repository.findByIdAndUserId("list-1", "user-1"))
                .thenReturn(Optional.of(watchlist));
        when(repository.save(any(WatchlistDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WatchlistDocument result = service.addItem("user-1", "list-1", "2885",
                "reliance", Segment.NSE_CASH);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().symbol()).isEqualTo("RELIANCE");
        assertThat(result.items().getFirst().itemId()).isNotBlank();
        verify(events).publishEvent(new WatchlistChangedEvent("user-1"));
    }

    @Test
    void removingAnUnknownItemIsNotFound() {
        WatchlistDocument watchlist = WatchlistDocument.create("user-1", "List",
                PlatformTestFixtures.NOW);
        when(repository.findByIdAndUserId("list-1", "user-1"))
                .thenReturn(Optional.of(watchlist));

        assertThatThrownBy(() -> service.removeItem("user-1", "list-1", "missing"))
                .isInstanceOf(PlatformException.class)
                .extracting(failure -> ((PlatformException) failure).errorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void deleteRemovesTheOwnedList() {
        WatchlistDocument watchlist = WatchlistDocument.create("user-1", "List",
                PlatformTestFixtures.NOW);
        when(repository.findByIdAndUserId("list-1", "user-1"))
                .thenReturn(Optional.of(watchlist));

        service.delete("user-1", "list-1");

        verify(repository).delete(watchlist);
        verify(events).publishEvent(new WatchlistChangedEvent("user-1"));
    }

    private static WatchlistDocument withItems(List<WatchlistItem> items) {
        List<WatchlistItem> copy = new ArrayList<>(items);
        return new WatchlistDocument("list-1", "user-1", "List", copy,
                PlatformTestFixtures.NOW, PlatformTestFixtures.NOW);
    }
}
