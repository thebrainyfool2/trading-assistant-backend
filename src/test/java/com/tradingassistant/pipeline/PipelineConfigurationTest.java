package com.tradingassistant.pipeline;

import com.tradingassistant.pipeline.calendar.MarketCalendarRegistry;
import com.tradingassistant.pipeline.event.MarketEventBus;
import com.tradingassistant.pipeline.session.IndicatorSessionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PipelineConfiguration.class)
            .withBean(Clock.class, () -> Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void disabledBrokerKeepsTheRuntimeInfrastructureAbsent() {
        runner.withPropertyValues("app.broker.angel-one.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MarketProcessingPipeline.class);
                    assertThat(context).doesNotHaveBean(IndicatorSessionRegistry.class);
                    assertThat(context).doesNotHaveBean(MarketEventBus.class);
                });
    }

    @Test
    void enabledBrokerWiresThePipelineAndItsCollaborators() {
        runner.withPropertyValues("app.broker.angel-one.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MarketCalendarRegistry.class);
                    assertThat(context).hasSingleBean(MarketEventBus.class);
                    assertThat(context).hasSingleBean(IndicatorSessionRegistry.class);
                    assertThat(context).hasSingleBean(MarketProcessingPipeline.class);
                    assertThat(context).hasSingleBean(MarketSessionLifecycleManager.class);
                    assertThat(context).hasSingleBean(com.tradingassistant.indicator.engine.IndicatorEngine.class);
                });
    }

    @Test
    void theWiredPipelineStartsEmptyAndAcceptsSubscriptions() {
        runner.withPropertyValues("app.broker.angel-one.enabled=true")
                .run(context -> {
                    MarketProcessingPipeline pipeline = context.getBean(MarketProcessingPipeline.class);
                    MarketSessionLifecycleManager lifecycle =
                            context.getBean(MarketSessionLifecycleManager.class);

                    assertThat(pipeline.channelCount()).isZero();
                    assertThat(pipeline.subscribedInstruments()).isEmpty();
                    assertThat(lifecycle.isStarted()).isFalse();
                    assertThat(lifecycle.startedAt()).isEmpty();
                });
    }
}
