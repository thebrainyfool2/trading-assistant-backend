package com.tradingassistant.intelligence.config;

import com.tradingassistant.intelligence.exception.IntelligenceValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketIntelligenceConfigTest {

    @Test
    void defaultsAreBalancedAndImmutable() {
        MarketIntelligenceConfig config = MarketIntelligenceConfig.defaults();

        assertThat(config.trendAgreementVotes()).isEqualTo(2);
        assertThat(config.trendAdxThreshold()).isEqualByComparingTo("25");
        assertThat(config.requireAdxForDirection()).isTrue();
        assertThat(config.momentumLowerThreshold()).isEqualByComparingTo("40");
        assertThat(config.momentumUpperThreshold()).isEqualByComparingTo("60");
        assertThat(config.volatilityChangeThreshold()).isEqualByComparingTo("0.05");
        assertThat(config.structureLookback()).isEqualTo(20);
        assertThat(config.historyWindow()).isEqualTo(120);
        assertThat(config.openingWindow()).isEqualTo(Duration.ofMinutes(15));
        assertThat(config.warmupPolicy()).isEqualTo(WarmupPolicy.PERMISSIVE);
        assertThat(config.requiredIndicators()).isEmpty();
        assertThat(config).isEqualTo(MarketIntelligenceConfig.defaults());
        assertThat(MarketIntelligenceConfig.MARKET_ASPECT_COUNT).isEqualTo(5);
    }

    @Test
    void builderOverridesOnlyWhatIsSet() {
        MarketIntelligenceConfig config = MarketIntelligenceConfig.builder()
                .warmupPolicy(WarmupPolicy.STRICT)
                .trendAgreementVotes(1)
                .build();

        assertThat(config.warmupPolicy()).isEqualTo(WarmupPolicy.STRICT);
        assertThat(config.trendAgreementVotes()).isEqualTo(1);
        assertThat(config.historyWindow()).isEqualTo(MarketIntelligenceConfig.defaults().historyWindow());
    }

    @Test
    void canonicalizesAndMatchesRequiredIndicators() {
        MarketIntelligenceConfig config = MarketIntelligenceConfig.builder()
                .requiredIndicators(Set.of("sma", " Ema "))
                .build();

        assertThat(config.requiredIndicators()).containsExactlyInAnyOrder("SMA", "EMA");
        assertThat(config.requires("sma")).isTrue();
        assertThat(config.requires("EMA")).isTrue();
        assertThat(config.requires("RSI")).isFalse();
        assertThat(config.requires(null)).isFalse();
    }

    @Test
    void rejectsInvalidThresholds() {
        assertThatThrownBy(() -> MarketIntelligenceConfig.builder().trendAgreementVotes(0).build())
                .isInstanceOf(IntelligenceValidationException.class);
        assertThatThrownBy(() -> MarketIntelligenceConfig.builder().trendAgreementVotes(4).build())
                .isInstanceOf(IntelligenceValidationException.class);
        assertThatThrownBy(() -> MarketIntelligenceConfig.builder()
                .momentumThresholds(new BigDecimal("60"), new BigDecimal("40")).build())
                .isInstanceOf(IntelligenceValidationException.class);
        assertThatThrownBy(() -> MarketIntelligenceConfig.builder()
                .momentumThresholds(BigDecimal.TEN, new BigDecimal("101")).build())
                .isInstanceOf(IntelligenceValidationException.class);
        assertThatThrownBy(() -> MarketIntelligenceConfig.builder()
                .volatilityChangeThreshold(new BigDecimal("-0.1")).build())
                .isInstanceOf(IntelligenceValidationException.class);
        assertThatThrownBy(() -> MarketIntelligenceConfig.builder()
                .trendAdxThreshold(null).build())
                .isInstanceOf(IntelligenceValidationException.class);
    }

    @Test
    void rejectsInvalidWindowsAndConfidenceThresholds() {
        assertThatThrownBy(() -> MarketIntelligenceConfig.builder().structureLookback(3).build())
                .isInstanceOf(IntelligenceValidationException.class);
        assertThatThrownBy(() -> MarketIntelligenceConfig.builder()
                .structureLookback(50).historyWindow(20).build())
                .isInstanceOf(IntelligenceValidationException.class);
        assertThatThrownBy(() -> MarketIntelligenceConfig.builder()
                .sessionWindows(Duration.ZERO, Duration.ofMinutes(5)).build())
                .isInstanceOf(IntelligenceValidationException.class);
        assertThatThrownBy(() -> MarketIntelligenceConfig.builder()
                .confidenceThresholds(3, 3).build())
                .isInstanceOf(IntelligenceValidationException.class);
        assertThatThrownBy(() -> MarketIntelligenceConfig.builder()
                .confidenceThresholds(0, 2).build())
                .isInstanceOf(IntelligenceValidationException.class);
        assertThatThrownBy(() -> MarketIntelligenceConfig.builder()
                .confidenceThresholds(2, 6).build())
                .isInstanceOf(IntelligenceValidationException.class);
        assertThatThrownBy(() -> MarketIntelligenceConfig.builder()
                .requiredIndicators(Set.of(" ")).build())
                .isInstanceOf(IntelligenceValidationException.class);
    }

    @Test
    void warmupPolicyDescribesItsBlockingBehaviour() {
        assertThat(WarmupPolicy.PERMISSIVE.blocksAssessment(
                com.tradingassistant.intelligence.domain.AssessmentStatus.PARTIAL)).isFalse();
        assertThat(WarmupPolicy.STRICT.blocksAssessment(
                com.tradingassistant.intelligence.domain.AssessmentStatus.PARTIAL)).isTrue();
        assertThat(WarmupPolicy.STRICT.blocksAssessment(
                com.tradingassistant.intelligence.domain.AssessmentStatus.READY)).isFalse();
    }
}
