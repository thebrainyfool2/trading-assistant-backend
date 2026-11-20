package com.tradingassistant.monitoring.config;

import com.tradingassistant.monitoring.MonitoringTestFixtures;
import com.tradingassistant.monitoring.domain.MonitoringInterval;
import com.tradingassistant.monitoring.exception.MonitoringValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MonitoringConfigTest {

    @Test
    void acceptsTheDocumentedDefaults() {
        MonitoringConfig config = MonitoringTestFixtures.config();

        assertThat(config.warningConfidence()).isEqualByComparingTo("65");
        assertThat(config.exitConfidence()).isEqualByComparingTo("45");
        assertThat(config.maximumStaleDuration()).isEqualTo(Duration.ofMinutes(2));
        assertThat(config.interval()).isEqualTo(MonitoringInterval.EVENT_DRIVEN);
    }

    @Test
    void rejectsExitAboveWarningAndInvalidThresholds() {
        assertThatThrownBy(() -> config(new BigDecimal("50"), new BigDecimal("60"),
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(MonitoringValidationException.class)
                .hasMessageContaining("exitConfidence");
        assertThatThrownBy(() -> config(new BigDecimal("101"), new BigDecimal("45"),
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(MonitoringValidationException.class)
                .hasMessageContaining("between 0 and 100");
        assertThatThrownBy(() -> config(new BigDecimal("65"), new BigDecimal("45"),
                new BigDecimal("-1"), BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(MonitoringValidationException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void propertiesConvertAndValidateTheMonitoringInterval() {
        MonitoringProperties properties = new MonitoringProperties(true,
                new BigDecimal("65"), new BigDecimal("45"), BigDecimal.TEN,
                new BigDecimal("8"), BigDecimal.TEN, 1, 3, new BigDecimal("50"),
                Duration.ofMinutes(2), "event_driven");

        MonitoringConfig config = properties.toConfig();

        assertThat(config.interval()).isEqualTo(MonitoringInterval.EVENT_DRIVEN);
        assertThat(config.componentDeterioration()).isEqualByComparingTo("10");
        assertThat(config.strategyDisagreementScore()).isEqualByComparingTo("50");
    }

    @Test
    void propertiesRejectUnknownOrMissingInterval() {
        assertThatThrownBy(() -> properties("POLLING").toConfig())
                .isInstanceOf(MonitoringValidationException.class)
                .hasMessageContaining("unsupported");
        assertThatThrownBy(() -> properties("").toConfig())
                .isInstanceOf(MonitoringValidationException.class)
                .hasMessageContaining("monitoring-interval");
    }

    private static MonitoringProperties properties(String interval) {
        return new MonitoringProperties(true, new BigDecimal("65"), new BigDecimal("45"),
                BigDecimal.TEN, new BigDecimal("8"), BigDecimal.TEN, 1, 3,
                new BigDecimal("50"), Duration.ofMinutes(2), interval);
    }

    private static MonitoringConfig config(BigDecimal warning, BigDecimal exit,
                                           BigDecimal confidence, BigDecimal edge,
                                           BigDecimal component) {
        return new MonitoringConfig(true, warning, exit, confidence, edge, component,
                1, 3, new BigDecimal("50"), Duration.ofMinutes(2),
                MonitoringInterval.EVENT_DRIVEN);
    }
}