package com.tradingassistant.platform.executionapp;

import com.tradingassistant.platform.PlatformTestFixtures;
import com.tradingassistant.platform.history.PortfolioSnapshotDocument;
import com.tradingassistant.platform.history.PortfolioSnapshotService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PortfolioQueryServiceTest {

    private final ExecutionGateway gateway = mock(ExecutionGateway.class);
    private final PortfolioSnapshotService snapshots = mock(PortfolioSnapshotService.class);
    private final PortfolioQueryService service = new PortfolioQueryService(gateway,
            snapshots);

    @Test
    void liveEnginePortfolioWinsOverStoredSnapshots() {
        when(gateway.snapshot()).thenReturn(Optional.of(
                PlatformTestFixtures.portfolio()));

        PortfolioQueryService.PortfolioView view = service.current();

        assertThat(view.source()).isEqualTo(PortfolioQueryService.SOURCE_LIVE);
        assertThat(view.capital()).isEqualByComparingTo("100000");
        verifyNoInteractions(snapshots);
    }

    @Test
    void storedSnapshotIsUsedWhenTheEngineIsOffline() {
        PortfolioSnapshotDocument document = PlatformTestFixtures.portfolioSnapshot();
        when(gateway.snapshot()).thenReturn(Optional.empty());
        when(snapshots.latest()).thenReturn(Optional.of(document));

        PortfolioQueryService.PortfolioView view = service.current();

        assertThat(view.source()).isEqualTo(PortfolioQueryService.SOURCE_SNAPSHOT);
        assertThat(view.capturedAt()).isEqualTo(PlatformTestFixtures.NOW);
    }

    @Test
    void emptyPortfolioIsReportedWhenNothingIsAvailable() {
        when(gateway.snapshot()).thenReturn(Optional.empty());
        when(snapshots.latest()).thenReturn(Optional.empty());

        PortfolioQueryService.PortfolioView view = service.current();

        assertThat(view.source()).isEqualTo(PortfolioQueryService.SOURCE_UNAVAILABLE);
        assertThat(view.capital()).isZero();
        assertThat(view.positions()).isEmpty();
    }

    @Test
    void openPositionsAreProjectedFromTheLiveSnapshot() {
        when(gateway.snapshot()).thenReturn(Optional.of(new
                com.tradingassistant.execution.domain.PortfolioSnapshot(
                PlatformTestFixtures.amount("100000"),
                PlatformTestFixtures.amount("90000"),
                PlatformTestFixtures.amount("1000"),
                PlatformTestFixtures.amount("9000"), PlatformTestFixtures.amount("0"),
                PlatformTestFixtures.amount("0"), PlatformTestFixtures.amount("45000"),
                java.util.List.of(PlatformTestFixtures.openPosition()),
                java.util.List.of())));

        assertThat(service.positions()).hasSize(1);
        assertThat(service.positions().getFirst().positionId()).isEqualTo("POS-1");
    }
}
