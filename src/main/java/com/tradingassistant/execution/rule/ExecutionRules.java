package com.tradingassistant.execution.rule;

import com.tradingassistant.execution.api.ExecutionRule;
import com.tradingassistant.execution.domain.ExecutionRuleContext;
import com.tradingassistant.execution.domain.ExecutionRuleEvaluation;
import com.tradingassistant.execution.domain.OrderSide;
import com.tradingassistant.execution.domain.OrderType;
import com.tradingassistant.execution.domain.PositionDirection;
import com.tradingassistant.execution.exception.ExecutionValidationException;
import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.intelligence.domain.SessionPhase;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable built-in execution rules.
 *
 * <p>Every rule is a record holding only its identifier and reads the policy from
 * the rule context. A rule checks one precondition of an order — its quantity,
 * prices, stop, target, session, broker, account, margin, duplication, position
 * count, leverage, product or type — and never decides whether the trade is
 * desirable.</p>
 */
public final class ExecutionRules {

    private ExecutionRules() {
    }

    /**
     * Boundary: quantity is positive and within the configured maximum.
     */
    public record ValidQuantity(String id) implements ExecutionRule {

        public ValidQuantity {
            id = Rules.requireId(id);
        }

        @Override
        public ExecutionRuleEvaluation evaluate(ExecutionRuleContext context) {
            var order = Rules.requireContext(context).order();
            BigDecimal quantity = order.quantity();
            BigDecimal maximum = context.config().maxOrderQuantity();
            var metadata = Support.metadata("quantity", Support.format(quantity),
                    "maximum", Support.format(maximum));
            if (quantity.signum() <= 0) {
                return ExecutionRuleEvaluation.failed(id,
                        "quantity must be greater than zero", metadata);
            }
            if (quantity.compareTo(maximum) > 0) {
                return ExecutionRuleEvaluation.failed(id,
                        "quantity exceeds the configured maximum", metadata);
            }
            return ExecutionRuleEvaluation.passed(id, "quantity is valid", metadata);
        }
    }

    /**
     * Boundary: the price the order type requires is present and positive.
     */
    public record ValidPrice(String id) implements ExecutionRule {

        public ValidPrice {
            id = Rules.requireId(id);
        }

        @Override
        public ExecutionRuleEvaluation evaluate(ExecutionRuleContext context) {
            var order = Rules.requireContext(context).order();
            OrderType type = order.type();
            var metadata = Support.metadata("type", type.name(),
                    "limitPrice", order.limitPrice().map(Support::format)
                            .orElse("absent"),
                    "referencePrice", Support.format(context.referencePrice()));
            if (context.referencePrice().signum() <= 0) {
                return ExecutionRuleEvaluation.insufficient(id,
                        "reference price is unavailable");
            }
            if (type == OrderType.LIMIT || type == OrderType.STOP_LIMIT) {
                if (order.limitPrice().isEmpty()) {
                    return ExecutionRuleEvaluation.failed(id,
                            "limit price is required for " + type, metadata);
                }
                if (order.limitPrice().get().signum() <= 0) {
                    return ExecutionRuleEvaluation.failed(id,
                            "limit price must be greater than zero", metadata);
                }
            }
            return ExecutionRuleEvaluation.passed(id, "price is valid", metadata);
        }
    }

    /**
     * Boundary: a protective stop exists and sits on the losing side of the
     * reference price.
     */
    public record ValidStop(String id) implements ExecutionRule {

        public ValidStop {
            id = Rules.requireId(id);
        }

        @Override
        public ExecutionRuleEvaluation evaluate(ExecutionRuleContext context) {
            var order = Rules.requireContext(context).order();
            if (order.stopPrice().isEmpty()) {
                return ExecutionRuleEvaluation.failed(id, "stop price is missing",
                        Support.metadata("stopPrice", "absent"));
            }
            BigDecimal stop = order.stopPrice().get();
            BigDecimal reference = context.referencePrice();
            var metadata = Support.metadata("stopPrice", Support.format(stop),
                    "referencePrice", Support.format(reference),
                    "side", order.side().name());
            boolean protective = order.side() == OrderSide.BUY
                    ? stop.compareTo(reference) < 0
                    : stop.compareTo(reference) > 0;
            if (!protective) {
                return ExecutionRuleEvaluation.failed(id,
                        "stop price is not protective for a " + order.side() + " order",
                        metadata);
            }
            return ExecutionRuleEvaluation.passed(id, "stop price is protective", metadata);
        }
    }

    /**
     * Boundary: an optional target sits on the profitable side of the reference
     * price.
     */
    public record ValidTarget(String id) implements ExecutionRule {

        public ValidTarget {
            id = Rules.requireId(id);
        }

        @Override
        public ExecutionRuleEvaluation evaluate(ExecutionRuleContext context) {
            var order = Rules.requireContext(context).order();
            if (order.targetPrice().isEmpty()) {
                return ExecutionRuleEvaluation.passed(id, "no target is set",
                        Support.metadata("target", "absent"));
            }
            BigDecimal target = order.targetPrice().get();
            BigDecimal reference = context.referencePrice();
            var metadata = Support.metadata("targetPrice", Support.format(target),
                    "referencePrice", Support.format(reference));
            boolean profitable = order.side() == OrderSide.BUY
                    ? target.compareTo(reference) > 0
                    : target.compareTo(reference) < 0;
            if (!profitable) {
                return ExecutionRuleEvaluation.failed(id,
                        "target price is not on the profitable side", metadata);
            }
            return ExecutionRuleEvaluation.passed(id, "target price is valid", metadata);
        }
    }

    /**
     * Boundary: the exchange session is open for execution.
     */
    public record MarketOpen(String id) implements ExecutionRule {

        public MarketOpen {
            id = Rules.requireId(id);
        }

        @Override
        public ExecutionRuleEvaluation evaluate(ExecutionRuleContext context) {
            Rules.requireContext(context);
            if (!context.config().requireMarketOpen()) {
                return ExecutionRuleEvaluation.passed(id,
                        "market session is not required by policy",
                        Support.metadata("required", "false"));
            }
            var session = context.request().marketAssessment().session();
            if (session.status() != AssessmentStatus.READY) {
                return ExecutionRuleEvaluation.insufficient(id,
                        "session status is " + session.status());
            }
            SessionPhase phase = session.phase();
            var metadata = Support.metadata("phase", phase.name());
            return switch (phase) {
                case OPENING, REGULAR, CLOSING -> ExecutionRuleEvaluation.passed(id,
                        "market session is open", metadata);
                case PRE_OPEN, AFTER_MARKET, UNKNOWN -> ExecutionRuleEvaluation.failed(id,
                        "market session is not open", metadata);
            };
        }
    }

    /**
     * Boundary: the selected broker adapter reports a connection.
     */
    public record BrokerConnected(String id) implements ExecutionRule {

        public BrokerConnected {
            id = Rules.requireId(id);
        }

        @Override
        public ExecutionRuleEvaluation evaluate(ExecutionRuleContext context) {
            Rules.requireContext(context);
            var metadata = Support.metadata("broker", context.config().brokerName(),
                    "mode", context.config().mode().name());
            return context.brokerConnected()
                    ? ExecutionRuleEvaluation.passed(id, "broker is connected", metadata)
                    : ExecutionRuleEvaluation.failed(id, "broker is not connected", metadata);
        }
    }

    /**
     * Boundary: the account carries usable capital.
     */
    public record AccountReady(String id) implements ExecutionRule {

        public AccountReady {
            id = Rules.requireId(id);
        }

        @Override
        public ExecutionRuleEvaluation evaluate(ExecutionRuleContext context) {
            Rules.requireContext(context);
            var capital = context.portfolio().capital();
            var metadata = Support.metadata("capital", Support.format(capital));
            if (capital.signum() <= 0) {
                return ExecutionRuleEvaluation.failed(id, "account capital is not positive",
                        metadata);
            }
            if (!context.accountReady()) {
                return ExecutionRuleEvaluation.failed(id, "account is not ready", metadata);
            }
            return ExecutionRuleEvaluation.passed(id, "account is ready", metadata);
        }
    }

    /**
     * Boundary: available capital covers the margin the order requires.
     */
    public record SufficientMargin(String id) implements ExecutionRule {

        public SufficientMargin {
            id = Rules.requireId(id);
        }

        @Override
        public ExecutionRuleEvaluation evaluate(ExecutionRuleContext context) {
            Rules.requireContext(context);
            BigDecimal available = context.portfolio().availableCapital();
            BigDecimal required = context.requiredMargin();
            var metadata = Support.metadata("requiredMargin", Support.format(required),
                    "availableCapital", Support.format(available));
            if (available.signum() <= 0) {
                return ExecutionRuleEvaluation.insufficient(id,
                        "available capital is not positive");
            }
            if (required.compareTo(available) > 0) {
                return ExecutionRuleEvaluation.failed(id,
                        "available capital does not cover the required margin", metadata);
            }
            return ExecutionRuleEvaluation.passed(id, "margin is sufficient", metadata);
        }
    }

    /**
     * Boundary: no other active order exists for the same strategy, instrument and
     * side.
     */
    public record OrderNotDuplicate(String id) implements ExecutionRule {

        public OrderNotDuplicate {
            id = Rules.requireId(id);
        }

        @Override
        public ExecutionRuleEvaluation evaluate(ExecutionRuleContext context) {
            Rules.requireContext(context);
            var order = context.order();
            var metadata = Support.metadata("strategyId", order.strategyId(),
                    "instrument", order.instrument().symbolToken(),
                    "side", order.side().name());
            return context.duplicateOrder()
                    ? ExecutionRuleEvaluation.failed(id,
                            "an active order already exists for this side", metadata)
                    : ExecutionRuleEvaluation.passed(id, "order is not a duplicate", metadata);
        }
    }

    /**
     * Boundary: opening another position would stay inside the configured count.
     *
     * <p>An order that closes or reduces an existing position does not increase the
     * count and is always allowed by this rule.</p>
     */
    public record PositionLimit(String id) implements ExecutionRule {

        public PositionLimit {
            id = Rules.requireId(id);
        }

        @Override
        public ExecutionRuleEvaluation evaluate(ExecutionRuleContext context) {
            Rules.requireContext(context);
            var order = context.order();
            var existing = context.portfolio().position(order.strategyId(), order.instrument());
            boolean closing = existing.isPresent()
                    && existing.get().direction() != PositionDirection.of(order.side());
            int open = context.portfolio().openPositionCount();
            int maximum = context.config().maxOpenPositions();
            var metadata = Support.metadata("openPositions", Integer.toString(open),
                    "maximum", Integer.toString(maximum),
                    "closing", Boolean.toString(closing));
            if (closing) {
                return ExecutionRuleEvaluation.passed(id,
                        "order reduces an existing position", metadata);
            }
            if (open >= maximum) {
                return ExecutionRuleEvaluation.failed(id,
                        "maximum open positions reached", metadata);
            }
            return ExecutionRuleEvaluation.passed(id, "position limit is respected", metadata);
        }
    }

    /**
     * Boundary: the notional the order implies stays inside the leverage ceiling.
     */
    public record LeverageLimit(String id) implements ExecutionRule {

        public LeverageLimit {
            id = Rules.requireId(id);
        }

        @Override
        public ExecutionRuleEvaluation evaluate(ExecutionRuleContext context) {
            Rules.requireContext(context);
            BigDecimal capital = context.portfolio().capital();
            if (capital.signum() <= 0) {
                return ExecutionRuleEvaluation.insufficient(id, "capital is unavailable");
            }
            BigDecimal leverage = context.notional().divide(capital, MathContext.DECIMAL128);
            BigDecimal maximum = context.config().maxLeverage();
            var metadata = Support.metadata("leverage", Support.format(leverage),
                    "maximum", Support.format(maximum));
            if (leverage.compareTo(maximum) > 0) {
                return ExecutionRuleEvaluation.failed(id,
                        "leverage exceeds the configured maximum", metadata);
            }
            return ExecutionRuleEvaluation.passed(id, "leverage is within limits", metadata);
        }
    }

    /**
     * Boundary: the instrument's segment is permitted by policy.
     */
    public record ValidProduct(String id) implements ExecutionRule {

        public ValidProduct {
            id = Rules.requireId(id);
        }

        @Override
        public ExecutionRuleEvaluation evaluate(ExecutionRuleContext context) {
            var order = Rules.requireContext(context).order();
            var segment = order.instrument().segment();
            var metadata = Support.metadata("segment", segment.name());
            return context.config().allows(segment)
                    ? ExecutionRuleEvaluation.passed(id, "product is permitted", metadata)
                    : ExecutionRuleEvaluation.failed(id, "product is not permitted", metadata);
        }
    }

    /**
     * Boundary: the order type is permitted and carries the prices it requires.
     */
    public record ValidOrderType(String id) implements ExecutionRule {

        public ValidOrderType {
            id = Rules.requireId(id);
        }

        @Override
        public ExecutionRuleEvaluation evaluate(ExecutionRuleContext context) {
            var order = Rules.requireContext(context).order();
            var metadata = Support.metadata("type", order.type().name());
            if (!context.config().allows(order.type())) {
                return ExecutionRuleEvaluation.failed(id,
                        "order type is not permitted", metadata);
            }
            if (order.type() == OrderType.STOP || order.type() == OrderType.STOP_LIMIT) {
                if (order.triggerPrice().isEmpty()) {
                    return ExecutionRuleEvaluation.failed(id,
                            "trigger price is required for " + order.type(), metadata);
                }
            }
            if (order.type() == OrderType.STOP_LIMIT && order.limitPrice().isEmpty()) {
                return ExecutionRuleEvaluation.failed(id,
                        "limit price is required for STOP_LIMIT", metadata);
            }
            return ExecutionRuleEvaluation.passed(id, "order type is valid", metadata);
        }
    }

    /**
     * Shared validation used by every rule record in this package.
     */
    static final class Rules {

        private Rules() {
        }

        static String requireId(String value) {
            if (value == null || value.isBlank()) {
                throw new ExecutionValidationException("rule id must not be blank");
            }
            return value.trim();
        }

        static ExecutionRuleContext requireContext(ExecutionRuleContext context) {
            if (context == null) {
                throw new ExecutionValidationException("context must not be null");
            }
            return context;
        }
    }

    /**
     * Deterministic formatting helpers.
     */
    static final class Support {

        private Support() {
        }

        static String format(BigDecimal value) {
            if (value == null) {
                return "unavailable";
            }
            BigDecimal scaled = value.setScale(6, java.math.RoundingMode.HALF_UP)
                    .stripTrailingZeros();
            return scaled.scale() < 0 ? scaled.setScale(0).toPlainString()
                    : scaled.toPlainString();
        }

        static Map<String, String> metadata(String... keysAndValues) {
            if (keysAndValues.length % 2 != 0) {
                throw new IllegalArgumentException("metadata requires key/value pairs");
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (int index = 0; index < keysAndValues.length; index += 2) {
                values.put(keysAndValues[index], keysAndValues[index + 1]);
            }
            return Map.copyOf(values);
        }
    }
}
