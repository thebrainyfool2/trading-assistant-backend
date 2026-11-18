package com.tradingassistant.execution.engine;

import com.tradingassistant.execution.broker.BrokerAdapter;
import com.tradingassistant.execution.broker.BrokerAdapterRegistry;
import com.tradingassistant.execution.broker.BrokerOrderCommand;
import com.tradingassistant.execution.config.ExecutionConfig;
import com.tradingassistant.execution.domain.ExecutionDiagnostics;
import com.tradingassistant.execution.domain.ExecutionReport;
import com.tradingassistant.execution.domain.ExecutionRequest;
import com.tradingassistant.execution.domain.ExecutionResult;
import com.tradingassistant.execution.domain.ExecutionRuleContext;
import com.tradingassistant.execution.domain.ExecutionRuleEvaluation;
import com.tradingassistant.execution.domain.ExecutionStatus;
import com.tradingassistant.execution.domain.OrderExecution;
import com.tradingassistant.execution.domain.OrderRequest;
import com.tradingassistant.execution.domain.OrderStatus;
import com.tradingassistant.execution.domain.PortfolioSnapshot;
import com.tradingassistant.execution.domain.TradeLifecycle;
import com.tradingassistant.execution.domain.TradeOrder;
import com.tradingassistant.execution.event.ExecutionEventSink;
import com.tradingassistant.execution.exception.ExecutionBrokerException;
import com.tradingassistant.execution.exception.ExecutionException;
import com.tradingassistant.execution.exception.ExecutionValidationException;
import com.tradingassistant.execution.order.OrderStateMachine;
import com.tradingassistant.execution.order.OrderStore;
import com.tradingassistant.execution.portfolio.PortfolioChange;
import com.tradingassistant.execution.portfolio.PortfolioService;
import com.tradingassistant.execution.registry.ExecutionRuleRegistry;
import com.tradingassistant.execution.validation.ExecutionValidator;
import com.tradingassistant.pipeline.event.OrderAcceptedEvent;
import com.tradingassistant.pipeline.event.OrderCancelledEvent;
import com.tradingassistant.pipeline.event.OrderCreatedEvent;
import com.tradingassistant.pipeline.event.OrderFilledEvent;
import com.tradingassistant.pipeline.event.OrderPartiallyFilledEvent;
import com.tradingassistant.pipeline.event.OrderRejectedEvent;
import com.tradingassistant.pipeline.event.OrderSubmittedEvent;
import com.tradingassistant.pipeline.event.OrderValidatedEvent;
import com.tradingassistant.pipeline.event.PortfolioUpdatedEvent;
import com.tradingassistant.pipeline.event.PositionClosedEvent;
import com.tradingassistant.pipeline.event.PositionModifiedEvent;
import com.tradingassistant.pipeline.event.PositionOpenedEvent;
import com.tradingassistant.pipeline.event.TradeCompletedEvent;
import com.tradingassistant.risk.domain.RiskAssessment;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Orchestrates one execution attempt.
 *
 * <p>The engine is the only place where the stages meet: create, validate,
 * submit, interpret the broker report, update the portfolio and publish events.
 * It holds no market state and makes no trading decision; it enforces the
 * immutable policy and records exactly what happened.</p>
 *
 * <p>All state lives in the injected collaborators. The engine itself only keeps a
 * monotonically increasing order sequence, which makes order identities unique
 * within the process without any global counter.</p>
 */
public final class ExecutionEngine {

    private final ExecutionConfig config;
    private final ExecutionRuleRegistry rules;
    private final BrokerAdapterRegistry brokers;
    private final PortfolioService portfolio;
    private final OrderStore orders;
    private final ExecutionEventSink sink;
    private final Clock clock;
    private final AtomicLong orderSequence = new AtomicLong();

    public ExecutionEngine(ExecutionConfig config, ExecutionRuleRegistry rules,
                           BrokerAdapterRegistry brokers, PortfolioService portfolio,
                           OrderStore orders, ExecutionEventSink sink, Clock clock) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.rules = Objects.requireNonNull(rules, "rules must not be null");
        this.brokers = Objects.requireNonNull(brokers, "brokers must not be null");
        this.portfolio = Objects.requireNonNull(portfolio, "portfolio must not be null");
        this.orders = Objects.requireNonNull(orders, "orders must not be null");
        this.sink = Objects.requireNonNull(sink, "sink must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        ExecutionValidator.validateRules(rules.snapshot());
    }

    public ExecutionConfig config() {
        return config;
    }

    public PortfolioService portfolio() {
        return portfolio;
    }

    public OrderStore orders() {
        return orders;
    }

    /**
     * Runs one attempt: create, validate, submit, apply and publish.
     *
     * @throws ExecutionValidationException when the request cannot be attempted at
     *                                      all: execution disabled, no adapter, or a
     *                                      live configuration wired to paper
     */
    public ExecutionResult execute(ExecutionRequest request) {
        ExecutionValidator.validateRequest(request);
        BrokerAdapter adapter = resolveAdapter();
        Instant now = clock.instant();
        String orderId = "ORD-" + orderSequence.incrementAndGet();
        OrderRequest orderRequest = request.order();
        BigDecimal reference = referencePrice(orderRequest, request.riskAssessment());
        TradeOrder created = TradeOrder.created(orderRequest, orderId, now);
        sink.publish(new OrderCreatedEvent(created, now));

        PortfolioSnapshot snapshot = portfolio.snapshot();
        boolean duplicate = orders.hasActiveOrder(orderRequest.strategyId(),
                orderRequest.instrument(), orderRequest.side());
        ExecutionRuleContext context = new ExecutionRuleContext(request, snapshot, config,
                duplicate, adapter.connected(), snapshot.capital().signum() > 0, reference);
        List<ExecutionRuleEvaluation> evaluations = new ArrayList<>();
        for (var rule : rules.snapshot()) {
            evaluations.add(rule.evaluate(context));
        }
        List<String> warnings = evaluations.stream()
                .filter(evaluation -> !evaluation.passed())
                .map(evaluation -> evaluation.ruleId() + ": " + evaluation.reason())
                .toList();
        ExecutionDiagnostics diagnostics = diagnostics(orderId, request, reference,
                context.requiredMargin(), adapter, 0, warnings);
        if (!warnings.isEmpty()) {
            return reject(created, evaluations, String.join("; ", warnings), diagnostics, now);
        }
        if (!request.riskAssessment().withinLimits()) {
            String reason = "risk assessment is not within limits: "
                    + request.riskAssessment().status();
            return reject(created, evaluations, reason, diagnostics, now);
        }

        TradeOrder validated = transition(created, OrderStatus.VALIDATED, now);
        orders.put(validated);
        sink.publish(new OrderValidatedEvent(validated, now));

        portfolio.block(orderId, context.requiredMargin());
        TradeOrder submitted = transition(validated, OrderStatus.SUBMITTED, now);
        orders.put(submitted);
        sink.publish(new OrderSubmittedEvent(submitted, now));

        BrokerOrderCommand command = command(submitted, reference);
        ExecutionReport report = null;
        ExecutionBrokerException failure = null;
        int attempts = 0;
        while (attempts < config.maxSubmitAttempts()) {
            attempts++;
            try {
                report = adapter.placeOrder(command);
                failure = null;
                break;
            } catch (ExecutionBrokerException exception) {
                failure = exception;
                if (!exception.retryable()) {
                    break;
                }
            }
        }
        ExecutionDiagnostics finalDiagnostics = diagnostics(orderId, request, reference,
                context.requiredMargin(), adapter, attempts, List.of());
        if (report == null) {
            String reason = "broker submission failed: "
                    + (failure == null ? "unknown error" : failure.getMessage());
            return fail(submitted, evaluations, reason, finalDiagnostics, now);
        }
        orders.recordBrokerOrderId(orderId, report.brokerOrderId());
        return applyReport(submitted, report, evaluations, finalDiagnostics, reference);
    }

    /**
     * Cancels a working order and releases the margin it blocked.
     *
     * @throws ExecutionValidationException when the order is unknown or not active
     * @throws ExecutionException           when the broker fails the cancellation
     */
    public ExecutionResult cancel(String orderId) {
        ExecutionValidator.validateOrderId(orderId);
        BrokerAdapter adapter = resolveAdapter();
        TradeOrder order = orders.find(orderId).orElseThrow(() ->
                new ExecutionValidationException("unknown order " + orderId));
        if (!order.active()) {
            throw new ExecutionValidationException("order " + orderId + " is not active");
        }
        Instant at = clock.instant();
        ExecutionReport report;
        try {
            report = adapter.cancelOrder(orders.brokerOrderId(orderId).orElse(orderId));
        } catch (ExecutionBrokerException exception) {
            throw new ExecutionException("broker cancellation failed: "
                    + exception.getMessage(), exception);
        }
        portfolio.release(orderId);
        TradeOrder cancelled = order.withStatus(OrderStatus.CANCELLED, at)
                .withReason("cancelled by request", at);
        orders.put(cancelled);
        sink.publish(new OrderCancelledEvent(cancelled, "cancelled by request", at));
        ExecutionDiagnostics diagnostics = new ExecutionDiagnostics(
                Map.of("orderId", orderId, "broker", adapter.brokerName(),
                        "attempts", "1"), List.of(), 1);
        return new ExecutionResult(ExecutionStatus.CANCELLED, Optional.of(cancelled),
                Optional.empty(), portfolio.snapshot(), List.of(), Optional.of(report),
                Optional.of("cancelled by request"), diagnostics);
    }

    private ExecutionResult applyReport(TradeOrder order, ExecutionReport report,
                                        List<ExecutionRuleEvaluation> evaluations,
                                        ExecutionDiagnostics diagnostics,
                                        BigDecimal reference) {
        Instant at = clock.instant();
        return switch (report.status()) {
            case FILLED, PARTIALLY_FILLED ->
                    applyFill(order, report, evaluations, diagnostics, reference, at);
            case REJECTED -> rejectFromBroker(order, report, evaluations, diagnostics);
            case CANCELLED, EXPIRED -> cancelFromBroker(order, report, evaluations,
                    diagnostics);
            case FAILED -> fail(order, evaluations,
                    "broker reported failure: " + report.message().orElse("unspecified"),
                    diagnostics, at);
            case CREATED, VALIDATED, SUBMITTED, PENDING ->
                    accept(order, report, evaluations, diagnostics, at);
        };
    }

    private ExecutionResult applyFill(TradeOrder order, ExecutionReport report,
                                      List<ExecutionRuleEvaluation> evaluations,
                                      ExecutionDiagnostics diagnostics,
                                      BigDecimal reference, Instant at) {
        BigDecimal cumulative = report.filledQuantity();
        if (cumulative.signum() <= 0 || cumulative.compareTo(order.quantity()) > 0) {
            return fail(order, evaluations,
                    "broker reported an invalid fill quantity " + cumulative,
                    diagnostics, at);
        }
        OrderExecution execution = execution(report, order, reference);
        OrderStatus newStatus = report.status() == OrderStatus.FILLED
                ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
        TradeOrder filled = order.withFill(cumulative, execution.price(), newStatus, at);
        orders.put(filled);
        if (newStatus == OrderStatus.FILLED) {
            sink.publish(new OrderFilledEvent(filled, execution, at));
        } else {
            sink.publish(new OrderPartiallyFilledEvent(filled, execution, at));
        }
        PortfolioChange change = portfolio.applyFill(filled, execution);
        if (newStatus == OrderStatus.PARTIALLY_FILLED) {
            BigDecimal remainingNotional = filled.remainingQuantity().multiply(reference);
            portfolio.block(filled.orderId(), config.marginFor(remainingNotional));
        }
        publishPositionEvent(change, at);
        sink.publish(new PortfolioUpdatedEvent(change.snapshot(), at));
        if (change.closed()) {
            sink.publish(new TradeCompletedEvent(TradeLifecycle.of(filled, change.position()),
                    at));
        }
        ExecutionStatus status = newStatus == OrderStatus.FILLED
                ? ExecutionStatus.COMPLETED : ExecutionStatus.PARTIAL;
        return new ExecutionResult(status, Optional.of(filled),
                Optional.of(change.position()), portfolio.snapshot(), evaluations,
                Optional.of(report), Optional.empty(), diagnostics);
    }

    private ExecutionResult rejectFromBroker(TradeOrder order, ExecutionReport report,
                                             List<ExecutionRuleEvaluation> evaluations,
                                             ExecutionDiagnostics diagnostics) {
        Instant at = clock.instant();
        portfolio.release(order.orderId());
        String reason = report.message().orElse("broker rejected the order");
        TradeOrder rejected = order.withStatus(OrderStatus.REJECTED, at)
                .withReason(reason, at);
        orders.put(rejected);
        sink.publish(new OrderRejectedEvent(rejected, reason, at));
        return new ExecutionResult(ExecutionStatus.REJECTED, Optional.of(rejected),
                Optional.empty(), portfolio.snapshot(), evaluations, Optional.of(report),
                Optional.of(reason), diagnostics);
    }

    private ExecutionResult cancelFromBroker(TradeOrder order, ExecutionReport report,
                                             List<ExecutionRuleEvaluation> evaluations,
                                             ExecutionDiagnostics diagnostics) {
        Instant at = clock.instant();
        portfolio.release(order.orderId());
        String reason = report.message().orElse("broker reported " + report.status());
        TradeOrder cancelled = order.withStatus(OrderStatus.CANCELLED, at)
                .withReason(reason, at);
        orders.put(cancelled);
        sink.publish(new OrderCancelledEvent(cancelled, reason, at));
        return new ExecutionResult(ExecutionStatus.CANCELLED, Optional.of(cancelled),
                Optional.empty(), portfolio.snapshot(), evaluations, Optional.of(report),
                Optional.of(reason), diagnostics);
    }

    private ExecutionResult accept(TradeOrder order, ExecutionReport report,
                                   List<ExecutionRuleEvaluation> evaluations,
                                   ExecutionDiagnostics diagnostics, Instant at) {
        TradeOrder pending = transition(order, OrderStatus.PENDING, at);
        orders.put(pending);
        sink.publish(new OrderAcceptedEvent(pending, report, at));
        return new ExecutionResult(ExecutionStatus.PENDING, Optional.of(pending),
                Optional.empty(), portfolio.snapshot(), evaluations, Optional.of(report),
                Optional.empty(), diagnostics);
    }

    private ExecutionResult reject(TradeOrder order,
                                   List<ExecutionRuleEvaluation> evaluations, String reason,
                                   ExecutionDiagnostics diagnostics, Instant at) {
        portfolio.release(order.orderId());
        TradeOrder rejected = order.withStatus(OrderStatus.REJECTED, at)
                .withReason(reason, at);
        orders.put(rejected);
        sink.publish(new OrderRejectedEvent(rejected, reason, at));
        return new ExecutionResult(ExecutionStatus.REJECTED, Optional.of(rejected),
                Optional.empty(), portfolio.snapshot(), evaluations, Optional.empty(),
                Optional.of(reason), diagnostics);
    }

    private ExecutionResult fail(TradeOrder order,
                                 List<ExecutionRuleEvaluation> evaluations, String reason,
                                 ExecutionDiagnostics diagnostics, Instant at) {
        portfolio.release(order.orderId());
        TradeOrder failed = order.withStatus(OrderStatus.FAILED, at).withReason(reason, at);
        orders.put(failed);
        sink.publish(new OrderRejectedEvent(failed, reason, at));
        return new ExecutionResult(ExecutionStatus.FAILED, Optional.of(failed),
                Optional.empty(), portfolio.snapshot(), evaluations, Optional.empty(),
                Optional.of(reason), diagnostics);
    }

    private void publishPositionEvent(PortfolioChange change, Instant at) {
        if (change.opened()) {
            sink.publish(new PositionOpenedEvent(change.position(), at));
        } else if (change.closed()) {
            sink.publish(new PositionClosedEvent(change.position(), at));
        } else {
            sink.publish(new PositionModifiedEvent(change.position(), at));
        }
    }

    private BrokerAdapter resolveAdapter() {
        if (!config.enabled()) {
            throw new ExecutionValidationException("execution is disabled");
        }
        BrokerAdapter adapter = brokers.select(config.brokerName());
        ExecutionValidator.validateMode(config, adapter);
        return adapter;
    }

    private static TradeOrder transition(TradeOrder order, OrderStatus status, Instant at) {
        OrderStateMachine.requireTransition(order.status(), status);
        return order.withStatus(status, at);
    }

    private static BigDecimal referencePrice(OrderRequest order, RiskAssessment assessment) {
        return order.limitPrice()
                .or(() -> order.triggerPrice())
                .orElseGet(() -> assessment.sizing().referencePrice());
    }

    private static BrokerOrderCommand command(TradeOrder order, BigDecimal reference) {
        return new BrokerOrderCommand(order.orderId(), order.instrument(), order.side(),
                order.type(), order.remainingQuantity(), order.limitPrice(),
                order.triggerPrice(), order.timeInForce(), reference);
    }

    private static OrderExecution execution(ExecutionReport report, TradeOrder order,
                                            BigDecimal reference) {
        if (!report.executions().isEmpty()) {
            return report.executions().getFirst();
        }
        BigDecimal price = report.averagePrice().orElse(reference);
        return new OrderExecution(report.brokerOrderId() + "-fill", order.orderId(),
                report.filledQuantity(), price, report.timestamp());
    }

    private ExecutionDiagnostics diagnostics(String orderId, ExecutionRequest request,
                                             BigDecimal reference, BigDecimal margin,
                                             BrokerAdapter adapter, int attempts,
                                             List<String> warnings) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("orderId", orderId);
        values.put("strategyId", request.order().strategyId());
        values.put("instrument", request.order().instrument().symbolToken());
        values.put("side", request.order().side().name());
        values.put("type", request.order().type().name());
        values.put("quantity", request.order().quantity().toPlainString());
        values.put("riskStatus", request.riskAssessment().status().name());
        values.put("marketStatus", request.marketAssessment().status().name());
        values.put("referencePrice", reference.toPlainString());
        values.put("requiredMargin", margin.toPlainString());
        values.put("broker", adapter.brokerName());
        values.put("mode", config.mode().name());
        values.put("attempts", Integer.toString(attempts));
        return new ExecutionDiagnostics(values, warnings, attempts);
    }
}
