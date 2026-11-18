package com.tradingassistant.strategy.config;

import com.tradingassistant.strategy.exception.StrategyValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyEngineConfigTest {

    @Test
    void exposesDocumentedDefaults() {
        StrategyEngineConfig config = StrategyEngineConfig.DEFAULT;

        assertThat(config.enabled()).isTrue();
        assertThat(config.parallelEvaluation()).isFalse();
        assertThat(config.maxStrategies()).isEqualTo(128);
        assertThat(config.maxRules()).isEqualTo(32);
        assertThat(config.failFast()).isFalse();
        assertThat(config.strictValidation()).isTrue();
    }

    @Test
    void appliesBuilderOverrides() {
        StrategyEngineConfig config = StrategyEngineConfig.builder()
                .enabled(false)
                .parallelEvaluation(true)
                .maxStrategies(4)
                .maxRules(2)
                .failFast(true)
                .strictValidation(false)
                .build();

        assertThat(config.enabled()).isFalse();
        assertThat(config.parallelEvaluation()).isTrue();
        assertThat(config.maxStrategies()).isEqualTo(4);
        assertThat(config.maxRules()).isEqualTo(2);
        assertThat(config.failFast()).isTrue();
        assertThat(config.strictValidation()).isFalse();
    }

    @Test
    void rebuildsFromAnExistingConfiguration() {
        StrategyEngineConfig modified = StrategyEngineConfig.DEFAULT.toBuilder()
                .maxRules(5)
                .build();

        assertThat(modified.maxRules()).isEqualTo(5);
        assertThat(modified.maxStrategies())
                .isEqualTo(StrategyEngineConfig.DEFAULT.maxStrategies());
        assertThat(modified.strictValidation()).isTrue();
    }

    @Test
    void rejectsInvalidLimits() {
        assertThatThrownBy(() -> StrategyEngineConfig.builder().maxStrategies(0).build())
                .isInstanceOf(StrategyValidationException.class)
                .hasMessageContaining("maxStrategies");
        assertThatThrownBy(() -> StrategyEngineConfig.builder().maxRules(0).build())
                .isInstanceOf(StrategyValidationException.class)
                .hasMessageContaining("maxRules");
        assertThatThrownBy(() -> StrategyEngineConfig.builder().maxStrategies(20_000).build())
                .isInstanceOf(StrategyValidationException.class)
                .hasMessageContaining("maxStrategies");
    }

    @Test
    void requiresANonNullConfiguration() {
        assertThatThrownBy(() -> StrategyEngineConfig.require(null))
                .isInstanceOf(NullPointerException.class);
        assertThat(StrategyEngineConfig.require(StrategyEngineConfig.DEFAULT))
                .isSameAs(StrategyEngineConfig.DEFAULT);
    }
}
