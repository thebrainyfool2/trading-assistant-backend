package com.tradingassistant.execution.config;

import com.tradingassistant.execution.domain.OrderType;
import com.tradingassistant.execution.domain.TimeInForce;
import com.tradingassistant.execution.exception.ExecutionValidationException;
import com.tradingassistant.market.domain.Segment;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable execution policy.
 *
 * <p>There is no runtime mutation. Every value is validated at construction so an
 * order can never be sized against a zero capital, a negative margin percentage or
 * an impossible retry policy.</p>
 *
 * @param enabled              whether execution runs at all
 * @param mode                 paper or live
 * @param brokerName           adapter to use
 * @param referenceCapital     capital the portfolio starts with
 * @param maxOpenPositions     maximum simultaneously open positions
 * @param maxLeverage          leverage ceiling
 * @param marginPercent        percentage of notional blocked as margin
 * @param maxOrderQuantity     largest quantity in one order
 * @param allowedOrderTypes    order types the policy permits
 * @param allowedSegments      segments the policy permits
 * @param defaultTimeInForce   time in force used when a request omits one
 * @param maxSubmitAttempts    broker submission attempts, including the first
 * @param timeoutMillis        per-submission timeout budget
 * @param maxRetainedOrders    bounded order history retained in memory
 * @param requireMarketOpen    whether the market session must be open
 */
public record ExecutionConfig(
        boolean enabled,
        ExecutionMode mode,
        String brokerName,
        BigDecimal referenceCapital,
        int maxOpenPositions,
        BigDecimal maxLeverage,
        BigDecimal marginPercent,
        BigDecimal maxOrderQuantity,
        Set<OrderType> allowedOrderTypes,
        Set<Segment> allowedSegments,
        TimeInForce defaultTimeInForce,
        int maxSubmitAttempts,
        long timeoutMillis,
        int maxRetainedOrders,
        boolean requireMarketOpen) {

    public static final ExecutionConfig DEFAULT = builder().build();

    public ExecutionConfig {
        mode = Objects.requireNonNull(mode, "mode must not be null");
        brokerName = requireText(brokerName, "brokerName");
        referenceCapital = requirePositive(referenceCapital, "referenceCapital");
        if (maxOpenPositions < 1 || maxOpenPositions > 1_000) {
            throw new ExecutionValidationException(
                    "maxOpenPositions must be between 1 and 1000");
        }
        maxLeverage = requirePositive(maxLeverage, "maxLeverage");
        if (maxLeverage.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ExecutionValidationException("maxLeverage must not exceed 100");
        }
        marginPercent = requirePositive(marginPercent, "marginPercent");
        if (marginPercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ExecutionValidationException("marginPercent must not exceed 100");
        }
        maxOrderQuantity = requirePositive(maxOrderQuantity, "maxOrderQuantity");
        allowedOrderTypes = allowedOrderTypes == null || allowedOrderTypes.isEmpty()
                ? Set.of() : Set.copyOf(EnumSet.copyOf(allowedOrderTypes));
        if (allowedOrderTypes.isEmpty()) {
            throw new ExecutionValidationException("allowedOrderTypes must not be empty");
        }
        allowedSegments = allowedSegments == null || allowedSegments.isEmpty()
                ? Set.of() : Set.copyOf(EnumSet.copyOf(allowedSegments));
        if (allowedSegments.isEmpty()) {
            throw new ExecutionValidationException("allowedSegments must not be empty");
        }
        defaultTimeInForce = Objects.requireNonNull(defaultTimeInForce,
                "defaultTimeInForce must not be null");
        if (maxSubmitAttempts < 1 || maxSubmitAttempts > 5) {
            throw new ExecutionValidationException(
                    "maxSubmitAttempts must be between 1 and 5");
        }
        if (timeoutMillis < 100 || timeoutMillis > 60_000) {
            throw new ExecutionValidationException(
                    "timeoutMillis must be between 100 and 60000");
        }
        if (maxRetainedOrders < 1 || maxRetainedOrders > 100_000) {
            throw new ExecutionValidationException(
                    "maxRetainedOrders must be between 1 and 100000");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .enabled(enabled)
                .mode(mode)
                .brokerName(brokerName)
                .referenceCapital(referenceCapital)
                .maxOpenPositions(maxOpenPositions)
                .maxLeverage(maxLeverage)
                .marginPercent(marginPercent)
                .maxOrderQuantity(maxOrderQuantity)
                .allowedOrderTypes(allowedOrderTypes)
                .allowedSegments(allowedSegments)
                .defaultTimeInForce(defaultTimeInForce)
                .maxSubmitAttempts(maxSubmitAttempts)
                .timeoutMillis(timeoutMillis)
                .maxRetainedOrders(maxRetainedOrders)
                .requireMarketOpen(requireMarketOpen);
    }

    /**
     * Margin required for a notional exposure.
     */
    public BigDecimal marginFor(BigDecimal notional) {
        if (notional == null) {
            return BigDecimal.ZERO;
        }
        return notional.multiply(marginPercent)
                .divide(BigDecimal.valueOf(100), MathContext.DECIMAL128);
    }

    public boolean allows(OrderType type) {
        return type != null && allowedOrderTypes.contains(type);
    }

    public boolean allows(Segment segment) {
        return segment != null && allowedSegments.contains(segment);
    }

    public static ExecutionConfig require(ExecutionConfig config) {
        return Objects.requireNonNull(config, "config must not be null");
    }

    private static BigDecimal requirePositive(BigDecimal value, String field) {
        if (value == null) {
            throw new ExecutionValidationException(field + " must not be null");
        }
        if (value.signum() <= 0) {
            throw new ExecutionValidationException(field + " must be greater than zero");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ExecutionValidationException(field + " must not be blank");
        }
        return value.trim();
    }

    /**
     * Mutable builder used only while constructing an immutable configuration.
     */
    public static final class Builder {

        private boolean enabled = true;
        private ExecutionMode mode = ExecutionMode.PAPER;
        private String brokerName = "paper";
        private BigDecimal referenceCapital = new BigDecimal("100000");
        private int maxOpenPositions = 5;
        private BigDecimal maxLeverage = new BigDecimal("5");
        private BigDecimal marginPercent = new BigDecimal("20");
        private BigDecimal maxOrderQuantity = new BigDecimal("10000");
        private Set<OrderType> allowedOrderTypes = Set.copyOf(EnumSet.allOf(OrderType.class));
        private Set<Segment> allowedSegments = Set.of(Segment.NSE_CASH, Segment.NSE_FO);
        private TimeInForce defaultTimeInForce = TimeInForce.DAY;
        private int maxSubmitAttempts = 2;
        private long timeoutMillis = 2_000L;
        private int maxRetainedOrders = 256;
        private boolean requireMarketOpen = true;

        private Builder() {
        }

        public Builder enabled(boolean value) {
            this.enabled = value;
            return this;
        }

        public Builder mode(ExecutionMode value) {
            this.mode = value;
            return this;
        }

        public Builder brokerName(String value) {
            this.brokerName = value;
            return this;
        }

        public Builder referenceCapital(BigDecimal value) {
            this.referenceCapital = value;
            return this;
        }

        public Builder maxOpenPositions(int value) {
            this.maxOpenPositions = value;
            return this;
        }

        public Builder maxLeverage(BigDecimal value) {
            this.maxLeverage = value;
            return this;
        }

        public Builder marginPercent(BigDecimal value) {
            this.marginPercent = value;
            return this;
        }

        public Builder maxOrderQuantity(BigDecimal value) {
            this.maxOrderQuantity = value;
            return this;
        }

        public Builder allowedOrderTypes(Set<OrderType> value) {
            this.allowedOrderTypes = value;
            return this;
        }

        public Builder allowedSegments(Set<Segment> value) {
            this.allowedSegments = value;
            return this;
        }

        public Builder defaultTimeInForce(TimeInForce value) {
            this.defaultTimeInForce = value;
            return this;
        }

        public Builder maxSubmitAttempts(int value) {
            this.maxSubmitAttempts = value;
            return this;
        }

        public Builder timeoutMillis(long value) {
            this.timeoutMillis = value;
            return this;
        }

        public Builder maxRetainedOrders(int value) {
            this.maxRetainedOrders = value;
            return this;
        }

        public Builder requireMarketOpen(boolean value) {
            this.requireMarketOpen = value;
            return this;
        }

        public ExecutionConfig build() {
            return new ExecutionConfig(enabled, mode, brokerName, referenceCapital,
                    maxOpenPositions, maxLeverage, marginPercent, maxOrderQuantity,
                    allowedOrderTypes, allowedSegments, defaultTimeInForce,
                    maxSubmitAttempts, timeoutMillis, maxRetainedOrders,
                    requireMarketOpen);
        }
    }
}
