package com.tradingassistant.qualification.config;

import com.tradingassistant.intelligence.domain.ConfidenceLevel;
import com.tradingassistant.qualification.exception.QualificationValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradeQualificationConfigTest {

    @Test
    void defaultsAreConservativeButFunctional() {
        TradeQualificationConfig config = TradeQualificationConfig.DEFAULT;

        assertThat(config.enabled()).isTrue();
        assertThat(config.strictValidation()).isTrue();
        assertThat(config.failFast()).isFalse();
        assertThat(config.minimumConfidence()).isEqualTo(ConfidenceLevel.HIGH);
        assertThat(config.requireOptions()).isFalse();
        assertThat(config.allowPartialQualification()).isFalse();
        assertThat(config.maxRules()).isEqualTo(16);
        assertThat(config.parallelEvaluation()).isFalse();
    }

    @Test
    void builderOverridesEveryKnob() {
        TradeQualificationConfig config = TradeQualificationConfig.builder()
                .enabled(false)
                .strictValidation(false)
                .failFast(true)
                .minimumConfidence(ConfidenceLevel.LOW)
                .requireOptions(true)
                .allowPartialQualification(true)
                .maxRules(4)
                .parallelEvaluation(true)
                .build();

        assertThat(config.enabled()).isFalse();
        assertThat(config.strictValidation()).isFalse();
        assertThat(config.failFast()).isTrue();
        assertThat(config.minimumConfidence()).isEqualTo(ConfidenceLevel.LOW);
        assertThat(config.requireOptions()).isTrue();
        assertThat(config.allowPartialQualification()).isTrue();
        assertThat(config.maxRules()).isEqualTo(4);
        assertThat(config.parallelEvaluation()).isTrue();
    }

    @Test
    void toBuilderRoundTrips() {
        TradeQualificationConfig config = TradeQualificationConfig.builder()
                .minimumConfidence(ConfidenceLevel.MEDIUM)
                .allowPartialQualification(true)
                .build();

        assertThat(config.toBuilder().build()).isEqualTo(config);
    }

    @Test
    void rejectsAnUnknownMinimumConfidence() {
        assertThatThrownBy(() -> TradeQualificationConfig.builder()
                .minimumConfidence(ConfidenceLevel.UNKNOWN)
                .build())
                .isInstanceOf(QualificationValidationException.class)
                .hasMessageContaining("minimumConfidence");
    }

    @Test
    void rejectsAnOutOfRangeRuleLimit() {
        assertThatThrownBy(() -> TradeQualificationConfig.builder().maxRules(0).build())
                .isInstanceOf(QualificationValidationException.class)
                .hasMessageContaining("maxRules");
        assertThatThrownBy(() -> TradeQualificationConfig.builder()
                .maxRules(TradeQualificationConfig.MAX_RULES_LIMIT + 1)
                .build())
                .isInstanceOf(QualificationValidationException.class)
                .hasMessageContaining("maxRules");
    }

    @Test
    void requireRejectsNull() {
        assertThatThrownBy(() -> TradeQualificationConfig.require(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("config");
    }
}
