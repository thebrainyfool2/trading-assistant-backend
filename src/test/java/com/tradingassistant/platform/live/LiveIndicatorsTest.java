package com.tradingassistant.platform.live;

import com.tradingassistant.indicator.engine.IndicatorEngine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LiveIndicatorsTest {

    @Test
    void everyRegisteredIndicatorIsInTheDecisionSet() {
        assertThat(LiveIndicators.ALL)
                .containsExactlyInAnyOrderElementsOf(IndicatorEngine.standard().supportedIndicators());
    }

    @Test
    void theSetIsStableAndContainsNoDuplicates() {
        assertThat(LiveIndicators.ALL).doesNotHaveDuplicates().hasSize(16);
        assertThat(LiveIndicators.asArray()).containsExactlyElementsOf(LiveIndicators.ALL);
    }
}
