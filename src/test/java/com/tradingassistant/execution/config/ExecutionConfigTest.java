package com.tradingassistant.execution.config;

import com.tradingassistant.execution.domain.OrderType;
import com.tradingassistant.execution.domain.TimeInForce;
import com.tradingassistant.execution.exception.ExecutionValidationException;
import com.tradingassistant.market.domain.Segment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionConfigTest {

    @Test
    void defaultsDescribeAPaperPolicy() {
        ExecutionConfig config = ExecutionConfig.DEFAULT;

        assertThat(config.enabled()).isTrue();
        assertThat(config.mode()).isEqualTo(ExecutionMode.PAPER);
        assertThat(config.brokerName()).isEqualTo("paper");
        assertThat(config.referenceCapital()).isEqualByComparingTo("100000");
        assertThat(config.maxOpenPositions()).isEqualTo(5);
        assertThat(config.maxLeverage()).isEqualByComparingTo("5");
        assertThat(config.marginPercent()).isEqualByComparingTo("20");
        assertThat(config.maxOrderQuantity()).isEqualByComparingTo("10000");
        assertThat(config.allowedOrderTypes())
                .containsExactlyInAnyOrder(OrderType.values());
        assertThat(config.allowedSegments()).containsExactlyInAnyOrder(Segment.NSE_CASH,
                Segment.NSE_FO);
        assertThat(config.defaultTimeInForce()).isEqualTo(TimeInForce.DAY);
        assertThat(config.maxSubmitAttempts()).isEqualTo(2);
        assertThat(config.timeoutMillis()).isEqualTo(2_000L);
        assertThat(config.maxRetainedOrders()).isEqualTo(256);
        assertThat(config.requireMarketOpen()).isTrue();
    }

    @Test
    void builderOverridesEveryValue() {
        ExecutionConfig config = ExecutionConfig.builder()
                .enabled(false)
                .mode(ExecutionMode.LIVE)
                .brokerName("live-broker")
                .referenceCapital(new BigDecimal("250000"))
                .maxOpenPositions(3)
                .maxLeverage(new BigDecimal("2"))
                .marginPercent(new BigDecimal("25"))
                .maxOrderQuantity(new BigDecimal("500"))
                .allowedOrderTypes(Set.of(OrderType.LIMIT))
                .allowedSegments(Set.of(Segment.NSE_FO))
                .defaultTimeInForce(TimeInForce.IOC)
                .maxSubmitAttempts(3)
                .timeoutMillis(1_500L)
                .maxRetainedOrders(64)
                .requireMarketOpen(false)
                .build();

        assertThat(config.enabled()).isFalse();
        assertThat(config.mode()).isEqualTo(ExecutionMode.LIVE);
        assertThat(config.brokerName()).isEqualTo("live-broker");
        assertThat(config.referenceCapital()).isEqualByComparingTo("250000");
        assertThat(config.maxOpenPositions()).isEqualTo(3);
        assertThat(config.maxLeverage()).isEqualByComparingTo("2");
        assertThat(config.marginPercent()).isEqualByComparingTo("25");
        assertThat(config.maxOrderQuantity()).isEqualByComparingTo("500");
        assertThat(config.allowedOrderTypes()).containsExactly(OrderType.LIMIT);
        assertThat(config.allowedSegments()).containsExactly(Segment.NSE_FO);
        assertThat(config.defaultTimeInForce()).isEqualTo(TimeInForce.IOC);
        assertThat(config.maxSubmitAttempts()).isEqualTo(3);
        assertThat(config.timeoutMillis()).isEqualTo(1_500L);
        assertThat(config.maxRetainedOrders()).isEqualTo(64);
        assertThat(config.requireMarketOpen()).isFalse();
    }

    @Test
    void marginIsAPercentageOfNotional() {
        assertThat(ExecutionConfig.DEFAULT.marginFor(new BigDecimal("50000")))
                .isEqualByComparingTo("10000");
        assertThat(ExecutionConfig.DEFAULT.marginFor(null)).isZero();
        assertThat(ExecutionConfig.DEFAULT.marginFor(BigDecimal.ZERO)).isZero();
    }

    @Test
    void allowsOrderTypesAndSegments() {
        ExecutionConfig config = ExecutionConfig.builder()
                .allowedOrderTypes(Set.of(OrderType.MARKET))
                .allowedSegments(Set.of(Segment.NSE_CASH))
                .build();

        assertThat(config.allows(OrderType.MARKET)).isTrue();
        assertThat(config.allows(OrderType.LIMIT)).isFalse();
        assertThat(config.allows((OrderType) null)).isFalse();
        assertThat(config.allows(Segment.NSE_CASH)).isTrue();
        assertThat(config.allows(Segment.NSE_FO)).isFalse();
        assertThat(config.allows((Segment) null)).isFalse();
    }

    @Test
    void toBuilderReproducesTheConfiguration() {
        ExecutionConfig original = ExecutionConfig.builder()
                .mode(ExecutionMode.LIVE)
                .maxOpenPositions(2)
                .build();

        ExecutionConfig copy = original.toBuilder().build();

        assertThat(copy).isEqualTo(original);
    }

    @Test
    void rejectsNonPositiveCapital() {
        assertThatThrownBy(() -> ExecutionConfig.builder()
                .referenceCapital(BigDecimal.ZERO).build())
                .isInstanceOf(ExecutionValidationException.class)
                .hasMessageContaining("referenceCapital");

        assertThatThrownBy(() -> ExecutionConfig.builder()
                .referenceCapital(new BigDecimal("-1")).build())
                .isInstanceOf(ExecutionValidationException.class);
    }

    @Test
    void rejectsImpossiblePositionCounts() {
        assertThatThrownBy(() -> ExecutionConfig.builder().maxOpenPositions(0).build())
                .isInstanceOf(ExecutionValidationException.class)
                .hasMessageContaining("maxOpenPositions");

        assertThatThrownBy(() -> ExecutionConfig.builder().maxOpenPositions(1_001).build())
                .isInstanceOf(ExecutionValidationException.class);
    }

    @Test
    void rejectsImpossibleLeverage() {
        assertThatThrownBy(() -> ExecutionConfig.builder()
                .maxLeverage(BigDecimal.ZERO).build())
                .isInstanceOf(ExecutionValidationException.class)
                .hasMessageContaining("maxLeverage");

        assertThatThrownBy(() -> ExecutionConfig.builder()
                .maxLeverage(new BigDecimal("101")).build())
                .isInstanceOf(ExecutionValidationException.class);
    }

    @Test
    void rejectsImpossibleMarginPercent() {
        assertThatThrownBy(() -> ExecutionConfig.builder()
                .marginPercent(BigDecimal.ZERO).build())
                .isInstanceOf(ExecutionValidationException.class)
                .hasMessageContaining("marginPercent");

        assertThatThrownBy(() -> ExecutionConfig.builder()
                .marginPercent(new BigDecimal("100.5")).build())
                .isInstanceOf(ExecutionValidationException.class);
    }

    @Test
    void rejectsEmptyAllowedSets() {
        assertThatThrownBy(() -> ExecutionConfig.builder()
                .allowedOrderTypes(Set.of()).build())
                .isInstanceOf(ExecutionValidationException.class)
                .hasMessageContaining("allowedOrderTypes");

        assertThatThrownBy(() -> ExecutionConfig.builder()
                .allowedSegments(Set.of()).build())
                .isInstanceOf(ExecutionValidationException.class)
                .hasMessageContaining("allowedSegments");
    }

    @Test
    void rejectsImpossibleRetryPolicy() {
        assertThatThrownBy(() -> ExecutionConfig.builder().maxSubmitAttempts(0).build())
                .isInstanceOf(ExecutionValidationException.class)
                .hasMessageContaining("maxSubmitAttempts");

        assertThatThrownBy(() -> ExecutionConfig.builder().maxSubmitAttempts(6).build())
                .isInstanceOf(ExecutionValidationException.class);
    }

    @Test
    void rejectsImpossibleTimeout() {
        assertThatThrownBy(() -> ExecutionConfig.builder().timeoutMillis(99L).build())
                .isInstanceOf(ExecutionValidationException.class)
                .hasMessageContaining("timeoutMillis");

        assertThatThrownBy(() -> ExecutionConfig.builder().timeoutMillis(60_001L).build())
                .isInstanceOf(ExecutionValidationException.class);
    }

    @Test
    void rejectsImpossibleRetention() {
        assertThatThrownBy(() -> ExecutionConfig.builder().maxRetainedOrders(0).build())
                .isInstanceOf(ExecutionValidationException.class)
                .hasMessageContaining("maxRetainedOrders");
    }

    @Test
    void rejectsBlankBrokerName() {
        assertThatThrownBy(() -> ExecutionConfig.builder().brokerName(" ").build())
                .isInstanceOf(ExecutionValidationException.class)
                .hasMessageContaining("brokerName");
    }

    @Test
    void trimsTheBrokerName() {
        assertThat(ExecutionConfig.builder().brokerName(" paper ").build().brokerName())
                .isEqualTo("paper");
    }

    @Test
    void requireRejectsNull() {
        assertThatThrownBy(() -> ExecutionConfig.require(null))
                .isInstanceOf(NullPointerException.class);
    }
}
