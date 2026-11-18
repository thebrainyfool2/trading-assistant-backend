package com.tradingassistant.execution.engine;

import com.tradingassistant.execution.ExecutionTestFixtures;
import com.tradingassistant.execution.ExecutionTestFixtures.Harness;
import com.tradingassistant.execution.broker.BrokerAdapter;
import com.tradingassistant.execution.broker.BrokerAdapterRegistry;
import com.tradingassistant.execution.broker.BrokerOrderCommand;
import com.tradingassistant.execution.broker.PaperBrokerAdapter;
import com.tradingassistant.execution.config.ExecutionConfig;
import com.tradingassistant.execution.config.ExecutionMode;
import com.tradingassistant.execution.domain.ExecutionReport;
import com.tradingassistant.execution.domain.ExecutionRequest;
import com.tradingassistant.execution.domain.ExecutionResult;
import com.tradingassistant.execution.domain.ExecutionStatus;
import com.tradingassistant.execution.domain.OrderExecution;
import com.tradingassistant.execution.domain.OrderRequest;
import com.tradingassistant.execution.domain.OrderSide;
import com.tradingassistant.execution.domain.OrderStatus;
import com.tradingassistant.execution.domain.Position;
import com.tradingassistant.execution.domain.PositionStatus;
import com.tradingassistant.execution.domain.TradeOrder;
import com.tradingassistant.execution.event.ExecutionEventSink;
import com.tradingassistant.execution.exception.ExecutionBrokerException;
import com.tradingassistant.execution.exception.ExecutionValidationException;
import com.tradingassistant.execution.order.OrderRequestFactory;
import com.tradingassistant.execution.order.OrderStore;
import com.tradingassistant.execution.portfolio.PortfolioService;
import com.tradingassistant.execution.registry.BuiltInExecutionRules;
import com.tradingassistant.execution.registry.ExecutionRuleRegistry;
import com.tradingassistant.pipeline.event.MarketEventType;
import com.tradingassistant.pipeline.event.OrderRejectedEvent;
import com.tradingassistant.pipeline.event.PositionClosedEvent;
import com.tradingassistant.pipeline.event.TradeCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionEngineTest {

    private Harness harness;

    @BeforeEach
    void setUp() {
        harness = ExecutionTestFixtures.harness();
    }

    private ExecutionResult execute() {
        return harness.engine().execute(ExecutionTestFixtures.executionRequest());
    }

    @Test
    void executesAFullMarketFill() {
        ExecutionResult result = execute();

        assertThat(result.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(result.accepted()).isTrue();
        assertThat(result.order()).isPresent();
        assertThat(result.order().orElseThrow().status()).isEqualTo(OrderStatus.FILLED);
        assertThat(result.order().orElseThrow().filledQuantity())
                .isEqualByComparingTo("100");
        assertThat(result.order().orElseThrow().averageFillPrice())
                .contains(new BigDecimal("500"));
        assertThat(result.position()).isPresent();
        assertThat(result.position().orElseThrow().status()).isEqualTo(PositionStatus.OPEN);
        assertThat(result.portfolio().openPositionCount()).isEqualTo(1);
        assertThat(result.portfolio().exposure()).isEqualByComparingTo("50000");
        assertThat(result.report()).isPresent();
    }

    @Test
    void publishesTheLifecycleInOrder() {
        execute();

        assertThat(harness.sink().types()).containsExactly(
                MarketEventType.ORDER_CREATED,
                MarketEventType.ORDER_VALIDATED,
                MarketEventType.ORDER_SUBMITTED,
                MarketEventType.ORDER_FILLED,
                MarketEventType.POSITION_OPENED,
                MarketEventType.PORTFOLIO_UPDATED);
    }

    @Test
    void recordsTheBrokerOrderIdentity() {
        ExecutionResult result = execute();

        String orderId = result.order().orElseThrow().orderId();
        assertThat(harness.orders().brokerOrderId(orderId)).isPresent();
    }

    @Test
    void rejectsAnOrderAboveTheConfiguredQuantity() {
        ExecutionConfig config = ExecutionConfig.builder()
                .maxOrderQuantity(new BigDecimal("50")).build();
        Harness limited = ExecutionTestFixtures.harness(config);

        ExecutionResult result = limited.engine()
                .execute(ExecutionTestFixtures.executionRequest());

        assertThat(result.status()).isEqualTo(ExecutionStatus.REJECTED);
        assertThat(result.reason().orElseThrow()).contains("VALID_QUANTITY");
        assertThat(result.report()).isEmpty();
        assertThat(limited.sink().count(MarketEventType.ORDER_REJECTED)).isEqualTo(1);
        assertThat(limited.portfolio().blockedMarginTotal()).isZero();
    }

    @Test
    void rejectsAnAssessmentOutsideItsRiskLimits() {
        var assessment = ExecutionTestFixtures.breachedAssessment();
        assertThat(assessment.withinLimits()).isFalse();
        OrderRequest order = OrderRequestFactory.create(
                ExecutionTestFixtures.marketIntent(), assessment);
        ExecutionRequest request = new ExecutionRequest(order, assessment,
                ExecutionTestFixtures.market());

        ExecutionResult result = harness.engine().execute(request);

        assertThat(result.status()).isEqualTo(ExecutionStatus.REJECTED);
        assertThat(result.reason().orElseThrow()).contains("risk assessment");
    }

    @Test
    void rejectsAnOrderTheBrokerRefuses() {
        harness.broker().rejectAll(true);

        ExecutionResult result = execute();

        assertThat(result.status()).isEqualTo(ExecutionStatus.REJECTED);
        assertThat(result.report().orElseThrow().status())
                .isEqualTo(OrderStatus.REJECTED);
        assertThat(result.order().orElseThrow().status())
                .isEqualTo(OrderStatus.REJECTED);
        assertThat(harness.portfolio().blockedMarginTotal()).isZero();
    }

    @Test
    void appliesAPartialFillAndKeepsTheRemainingMarginBlocked() {
        harness.broker().fillRatio(new BigDecimal("0.5"));

        ExecutionResult result = execute();

        assertThat(result.status()).isEqualTo(ExecutionStatus.PARTIAL);
        assertThat(result.order().orElseThrow().status())
                .isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(result.order().orElseThrow().filledQuantity())
                .isEqualByComparingTo("50");
        assertThat(result.position().orElseThrow().quantity()).isEqualByComparingTo("50");
        assertThat(result.portfolio().blockedMargin()).isEqualByComparingTo("5000");
        assertThat(harness.sink().count(MarketEventType.ORDER_PARTIALLY_FILLED))
                .isEqualTo(1);
    }

    @Test
    void retriesARetryableBrokerFailure() {
        harness.broker().failNext(1);

        ExecutionResult result = execute();

        assertThat(result.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(result.diagnostics().attempts()).isEqualTo(2);
        assertThat(harness.sink().count(MarketEventType.ORDER_SUBMITTED)).isEqualTo(1);
    }

    @Test
    void failsWhenEveryAttemptIsExhausted() {
        harness.broker().failNext(5);

        ExecutionResult result = execute();

        assertThat(result.status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(result.diagnostics().attempts()).isEqualTo(2);
        assertThat(result.order().orElseThrow().status()).isEqualTo(OrderStatus.FAILED);
        assertThat(harness.portfolio().blockedMarginTotal()).isZero();
        assertThat(harness.sink().count(MarketEventType.ORDER_REJECTED)).isEqualTo(1);
    }

    @Test
    void doesNotRetryANonRetryableFailure() {
        Custom custom = custom(command -> {
            throw new ExecutionBrokerException("permanent failure", false);
        });

        ExecutionResult result = custom.engine()
                .execute(ExecutionTestFixtures.executionRequest());

        assertThat(result.status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(result.diagnostics().attempts()).isEqualTo(1);
        assertThat(custom.portfolio().blockedMarginTotal()).isZero();
    }

    @Test
    void rejectsAnImpossibleFillReportedByTheBroker() {
        Custom custom = custom(command -> new ExecutionReport("B-1", OrderStatus.FILLED,
                new BigDecimal("200"), Optional.of(new BigDecimal("500")),
                Optional.of("overfill"), List.of(), ExecutionTestFixtures.START));

        ExecutionResult result = custom.engine()
                .execute(ExecutionTestFixtures.executionRequest());

        assertThat(result.status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(result.reason().orElseThrow()).contains("invalid fill quantity");
    }

    @Test
    void refusesToRunWhenExecutionIsDisabled() {
        Harness disabled = ExecutionTestFixtures.harness(
                ExecutionConfig.builder().enabled(false).build());

        assertThatThrownBy(() -> disabled.engine()
                .execute(ExecutionTestFixtures.executionRequest()))
                .isInstanceOf(ExecutionValidationException.class)
                .hasMessageContaining("disabled");
        assertThat(disabled.sink().events()).isEmpty();
    }

    @Test
    void refusesLiveModeWithThePaperAdapter() {
        Harness live = ExecutionTestFixtures.harness(
                ExecutionConfig.builder().mode(ExecutionMode.LIVE).build());

        assertThatThrownBy(() -> live.engine()
                .execute(ExecutionTestFixtures.executionRequest()))
                .isInstanceOf(ExecutionValidationException.class)
                .hasMessageContaining("LIVE");
    }

    @Test
    void refusesAnUnknownBrokerAdapter() {
        Harness unknown = ExecutionTestFixtures.harness(
                ExecutionConfig.builder().brokerName("missing").build());

        assertThatThrownBy(() -> unknown.engine()
                .execute(ExecutionTestFixtures.executionRequest()))
                .isInstanceOf(ExecutionValidationException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void cancelsAWorkingOrder() {
        Custom custom = custom(command -> new ExecutionReport("B-1", OrderStatus.PENDING,
                BigDecimal.ZERO, Optional.empty(), Optional.of("working"), List.of(),
                ExecutionTestFixtures.START));

        ExecutionResult pending = custom.engine()
                .execute(ExecutionTestFixtures.executionRequest());
        assertThat(pending.status()).isEqualTo(ExecutionStatus.PENDING);
        assertThat(custom.portfolio().blockedMarginTotal())
                .isEqualByComparingTo("10000");

        ExecutionResult cancelled = custom.engine().cancel(
                pending.order().orElseThrow().orderId());

        assertThat(cancelled.status()).isEqualTo(ExecutionStatus.CANCELLED);
        assertThat(cancelled.order().orElseThrow().status())
                .isEqualTo(OrderStatus.CANCELLED);
        assertThat(custom.portfolio().blockedMarginTotal()).isZero();
        assertThat(custom.sink().count(MarketEventType.ORDER_CANCELLED)).isEqualTo(1);
    }

    @Test
    void refusesToCancelAnUnknownOrder() {
        assertThatThrownBy(() -> harness.engine().cancel("ORD-404"))
                .isInstanceOf(ExecutionValidationException.class)
                .hasMessageContaining("unknown order");
    }

    @Test
    void refusesToCancelATerminalOrder() {
        ExecutionResult completed = execute();

        assertThatThrownBy(() -> harness.engine().cancel(
                completed.order().orElseThrow().orderId()))
                .isInstanceOf(ExecutionValidationException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void refusesToCancelABlankOrderId() {
        assertThatThrownBy(() -> harness.engine().cancel(" "))
                .isInstanceOf(ExecutionValidationException.class);
    }

    @Test
    void addsASecondFillToTheExistingPosition() {
        execute();
        ExecutionResult second = execute();

        assertThat(second.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(second.position().orElseThrow().quantity())
                .isEqualByComparingTo("200");
        assertThat(harness.portfolio().openPositionCount()).isEqualTo(1);
        assertThat(harness.sink().count(MarketEventType.POSITION_MODIFIED)).isEqualTo(1);
    }

    @Test
    void closesAPositionAndCompletesTheTrade() {
        execute();
        OrderRequest sell = OrderRequestFactory.create(
                ExecutionTestFixtures.marketIntent(OrderSide.SELL),
                ExecutionTestFixtures.assessment());
        ExecutionResult closing = harness.engine().execute(new ExecutionRequest(sell,
                ExecutionTestFixtures.assessment(), ExecutionTestFixtures.market()));

        assertThat(closing.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(closing.position().orElseThrow().status())
                .isEqualTo(PositionStatus.CLOSED);
        assertThat(harness.portfolio().openPositionCount()).isZero();
        assertThat(harness.portfolio().snapshot().closedPositionCount()).isEqualTo(1);
        assertThat(harness.sink().count(MarketEventType.POSITION_CLOSED)).isEqualTo(1);
        assertThat(harness.sink().count(MarketEventType.TRADE_COMPLETED)).isEqualTo(1);
        TradeCompletedEvent completed = harness.sink().last(TradeCompletedEvent.class)
                .orElseThrow();
        assertThat(completed.lifecycle().completed()).isTrue();
    }

    @Test
    void rejectsADuplicateWorkingOrder() {
        Custom custom = custom(command -> new ExecutionReport("B-1", OrderStatus.PENDING,
                BigDecimal.ZERO, Optional.empty(), Optional.of("working"), List.of(),
                ExecutionTestFixtures.START));

        custom.engine().execute(ExecutionTestFixtures.executionRequest());
        ExecutionResult duplicate = custom.engine()
                .execute(ExecutionTestFixtures.executionRequest());

        assertThat(duplicate.status()).isEqualTo(ExecutionStatus.REJECTED);
        assertThat(duplicate.reason().orElseThrow()).contains("ORDER_NOT_DUPLICATE");
        OrderRejectedEvent rejected = custom.sink().last(OrderRejectedEvent.class)
                .orElseThrow();
        assertThat(rejected.reason()).contains("ORDER_NOT_DUPLICATE");
    }

    @Test
    void diagnosticsDescribeTheAttempt() {
        ExecutionResult result = execute();

        var values = result.diagnostics().values();
        assertThat(values)
                .containsEntry("strategyId", ExecutionTestFixtures.STRATEGY_ID)
                .containsEntry("quantity", "100")
                .containsEntry("broker", "paper")
                .containsEntry("mode", "PAPER")
                .containsEntry("attempts", "1");
        assertThat(new BigDecimal(values.get("referencePrice")))
                .isEqualByComparingTo("500");
        assertThat(new BigDecimal(values.get("requiredMargin")))
                .isEqualByComparingTo("10000");
        assertThat(result.ruleEvaluations()).hasSize(13);
        assertThat(result.ruleEvaluations()).allMatch(evaluation -> evaluation.passed());
    }

    @Test
    void everyOrderGetsADistinctIdentity() {
        String first = execute().order().orElseThrow().orderId();
        String second = execute().order().orElseThrow().orderId();

        assertThat(first).isNotEqualTo(second);
        assertThat(harness.orders().size()).isEqualTo(2);
    }

    @Test
    void exposesItsCollaborators() {
        assertThat(harness.engine().config()).isEqualTo(ExecutionTestFixtures.config());
        assertThat(harness.engine().portfolio()).isSameAs(harness.portfolio());
        assertThat(harness.engine().orders()).isSameAs(harness.orders());
    }

    private static Custom custom(AdapterBehaviour behaviour) {
        ExecutionConfig config = ExecutionTestFixtures.config();
        BrokerAdapter adapter = new StubBroker(behaviour);
        BrokerAdapterRegistry brokers = new BrokerAdapterRegistry();
        brokers.register(adapter);
        PortfolioService portfolio = new PortfolioService(config);
        OrderStore orders = new OrderStore(config.maxRetainedOrders());
        ExecutionTestFixtures.RecordingSink sink =
                new ExecutionTestFixtures.RecordingSink();
        ExecutionEngine engine = new ExecutionEngine(config,
                new ExecutionRuleRegistry(BuiltInExecutionRules.all()), brokers, portfolio,
                orders, sink, ExecutionTestFixtures.clock());
        return new Custom(engine, sink, portfolio, orders);
    }

    @FunctionalInterface
    private interface AdapterBehaviour {
        ExecutionReport respond(BrokerOrderCommand command);
    }

    private record Custom(ExecutionEngine engine,
                          ExecutionTestFixtures.RecordingSink sink,
                          PortfolioService portfolio, OrderStore orders) {
    }

    private static final class StubBroker implements BrokerAdapter {

        private final AdapterBehaviour behaviour;

        private StubBroker(AdapterBehaviour behaviour) {
            this.behaviour = behaviour;
        }

        @Override
        public String brokerName() {
            return PaperBrokerAdapter.NAME;
        }

        @Override
        public boolean connected() {
            return true;
        }

        @Override
        public ExecutionReport placeOrder(BrokerOrderCommand command) {
            return behaviour.respond(command);
        }

        @Override
        public ExecutionReport cancelOrder(String brokerOrderId) {
            return new ExecutionReport(brokerOrderId, OrderStatus.CANCELLED,
                    BigDecimal.ZERO, Optional.empty(), Optional.of("cancelled"),
                    List.of(), ExecutionTestFixtures.START);
        }

        @Override
        public ExecutionReport modifyOrder(BrokerOrderCommand command) {
            return new ExecutionReport(command.orderId(), OrderStatus.PENDING,
                    BigDecimal.ZERO, Optional.empty(), Optional.of("modified"),
                    List.of(), ExecutionTestFixtures.START);
        }

        @Override
        public Optional<ExecutionReport> orderStatus(String brokerOrderId) {
            return Optional.empty();
        }

        @Override
        public List<Position> positions() {
            return List.of();
        }
    }
}
