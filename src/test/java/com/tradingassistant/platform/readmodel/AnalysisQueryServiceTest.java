package com.tradingassistant.platform.readmodel;

import com.tradingassistant.platform.PlatformTestFixtures;
import com.tradingassistant.strategy.domain.StrategyDefinition;
import com.tradingassistant.strategy.registry.StrategyRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalysisQueryServiceTest {

    private final PipelineSnapshotStore store = mock(PipelineSnapshotStore.class);
    private final ObjectProvider<StrategyRegistry> registryProvider =
            mock(ObjectProvider.class);
    private final AnalysisQueryService service = new AnalysisQueryService(store,
            registryProvider, Clock.fixed(PlatformTestFixtures.NOW,
            java.time.ZoneOffset.UTC));

    @Test
    void defaultLimitIsApplied() {
        service.markets(null);

        verify(store).marketAssessments(25);
    }

    @Test
    void limitIsClamped() {
        service.markets(-1);
        verify(store).marketAssessments(1);
        service.strategies(1_000);
        verify(store).strategyEvaluations(100);
    }

    @Test
    void definitionsAreEmptyWithoutTheEngineRegistry() {
        when(registryProvider.getIfAvailable()).thenReturn(null);

        assertThat(service.definitions()).isEmpty();
        assertThat(service.engineAvailable()).isFalse();
    }

    @Test
    void definitionsComeFromTheRegistryWhenAvailable() {
        StrategyRegistry registry = mock(StrategyRegistry.class);
        StrategyDefinition definition = mock(StrategyDefinition.class);
        when(registryProvider.getIfAvailable()).thenReturn(registry);
        when(registry.snapshot()).thenReturn(List.of(definition));

        assertThat(service.definitions()).containsExactly(definition);
        assertThat(service.engineAvailable()).isTrue();
    }

    @Test
    void readModelSizeAndClockAreExposed() {
        when(store.size()).thenReturn(7);

        assertThat(service.readModelEntries()).isEqualTo(7);
        assertThat(service.now()).isEqualTo(PlatformTestFixtures.NOW);
    }
}
