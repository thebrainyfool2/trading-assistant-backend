package com.tradingassistant.execution;

import com.tradingassistant.execution.broker.BrokerAdapterRegistry;
import com.tradingassistant.execution.broker.PaperBrokerAdapter;
import com.tradingassistant.execution.config.ExecutionConfig;
import com.tradingassistant.execution.domain.ExecutionRequest;
import com.tradingassistant.execution.domain.ExecutionRuleContext;
import com.tradingassistant.execution.domain.OrderIntent;
import com.tradingassistant.execution.domain.OrderRequest;
import com.tradingassistant.execution.domain.OrderSide;
import com.tradingassistant.execution.domain.OrderType;
import com.tradingassistant.execution.domain.PortfolioSnapshot;
import com.tradingassistant.execution.domain.TimeInForce;
import com.tradingassistant.execution.engine.ExecutionEngine;
import com.tradingassistant.execution.event.ExecutionEventSink;
import com.tradingassistant.execution.order.OrderRequestFactory;
import com.tradingassistant.execution.order.OrderStore;
import com.tradingassistant.execution.portfolio.PortfolioService;
import com.tradingassistant.execution.registry.BuiltInExecutionRules;
import com.tradingassistant.execution.registry.ExecutionRuleRegistry;
import com.tradingassistant.intelligence.domain.MarketAssessment;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.pipeline.event.MarketEvent;
import com.tradingassistant.pipeline.event.MarketEventType;
import com.tradingassistant.risk.RiskTestFixtures;
import com.tradingassistant.risk.config.RiskEngineConfig;
import com.tradingassistant.risk.domain.RiskAssessment;
import com.tradingassistant.risk.engine.RiskEngine;
import com.tradingassistant.risk.registry.BuiltInRiskRules;
import com.tradingassistant.risk.registry.RiskRuleRegistry;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Baseline execution inputs.
 *
 * <p>The risk assessment is produced by the real risk engine from the Phase 10
 * baseline, so the execution tests consume exactly the payload the pipeline would
 * publish: a 500.00 reference price, a 10.00 stop distance, a quantity of 100 and
 * a policy status of WITHIN_LIMITS.</p>
 */
public final class ExecutionTestFixtures {

    public static final InstrumentKey INSTRUMENT = RiskTestFixtures.SPOT;
    public static final Instant START = RiskTestFixtures.START;
    public static final String STRATEGY_ID = RiskTestFixtures.STRATEGY_ID;
    public static final BigDecimal QUANTITY = new BigDecimal("100");
    public static final BigDecimal REFERENCE = new BigDecimal("500");
    public static final BigDecimal STOP = new BigDecimal("490");

    private static final RiskEngine RISK_ENGINE = new RiskEngine(RiskEngineConfig.DEFAULT,
            new RiskRuleRegistry(BuiltInRiskRules.all()), Runnable::run);

    private ExecutionTestFixtures() {
    }

    public static RiskAssessment assessment() {
        return RISK_ENGINE.evaluate(RiskTestFixtures.context());
    }

    public static MarketAssessment market() {
        return RiskTestFixtures.marketAssessment();
    }

    /**
     * Assessment whose risk boundaries are breached, so execution must refuse it.
     */
    public static RiskAssessment breachedAssessment() {
        return RISK_ENGINE.evaluate(RiskTestFixtures.context(
                RiskTestFixtures.optionsAssessment(new BigDecimal("1"))));
    }

    public static OrderIntent marketIntent() {
        return OrderIntent.market(STRATEGY_ID, INSTRUMENT, OrderSide.BUY);
    }

    public static OrderIntent marketIntent(OrderSide side) {
        return OrderIntent.market(STRATEGY_ID, INSTRUMENT, side);
    }

    public static OrderRequest request() {
        return OrderRequestFactory.create(marketIntent(), assessment());
    }

    public static OrderRequest request(OrderIntent intent) {
        return OrderRequestFactory.create(intent, assessment());
    }

    public static ExecutionRequest executionRequest() {
        return new ExecutionRequest(request(), assessment(), market());
    }

    public static ExecutionConfig config() {
        return ExecutionConfig.DEFAULT;
    }

    public static Clock clock() {
        return Clock.fixed(START, ZoneOffset.UTC);
    }

    public static ExecutionRuleContext ruleContext(ExecutionRequest request,
                                                   PortfolioSnapshot snapshot,
                                                   ExecutionConfig config,
                                                   boolean duplicate,
                                                   boolean brokerConnected) {
        BigDecimal reference = request.order().limitPrice()
                .or(() -> request.order().triggerPrice())
                .orElseGet(() -> request.riskAssessment().sizing().referencePrice());
        return new ExecutionRuleContext(request, snapshot, config, duplicate,
                brokerConnected, snapshot.capital().signum() > 0, reference);
    }

    public static ExecutionRuleContext ruleContext() {
        ExecutionConfig config = config();
        return ruleContext(executionRequest(),
                PortfolioSnapshot.empty(config.referenceCapital()), config, false, true);
    }

    public static OrderRequest limitRequest(BigDecimal limit, BigDecimal stop,
                                            BigDecimal target) {
        return new OrderRequest(STRATEGY_ID, INSTRUMENT, OrderSide.BUY, OrderType.LIMIT,
                QUANTITY, Optional.of(limit), Optional.empty(), Optional.of(stop),
                Optional.ofNullable(target), TimeInForce.DAY, START);
    }

    /**
     * Engine with a paper broker, a recording sink and a fixed clock.
     */
    public static Harness harness() {
        return harness(config());
    }

    public static Harness harness(ExecutionConfig config) {
        PaperBrokerAdapter broker = new PaperBrokerAdapter(clock());
        BrokerAdapterRegistry brokers = new BrokerAdapterRegistry();
        brokers.register(broker);
        PortfolioService portfolio = new PortfolioService(config);
        OrderStore orders = new OrderStore(config.maxRetainedOrders());
        RecordingSink sink = new RecordingSink();
        ExecutionEngine engine = new ExecutionEngine(config,
                new ExecutionRuleRegistry(BuiltInExecutionRules.all()), brokers, portfolio,
                orders, sink, clock());
        return new Harness(engine, broker, portfolio, orders, sink, config);
    }

    /**
     * Collaborators of one engine under test.
     */
    public record Harness(ExecutionEngine engine, PaperBrokerAdapter broker,
                          PortfolioService portfolio, OrderStore orders,
                          RecordingSink sink, ExecutionConfig config) {
    }

    /**
     * Sink that keeps every published event.
     */
    public static final class RecordingSink implements ExecutionEventSink {

        private final List<MarketEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void publish(MarketEvent event) {
            events.add(event);
        }

        public List<MarketEvent> events() {
            return List.copyOf(events);
        }

        public List<MarketEventType> types() {
            return events.stream().map(MarketEvent::type).toList();
        }

        public long count(MarketEventType type) {
            return events.stream().filter(event -> event.type() == type).count();
        }

        public <T extends MarketEvent> Optional<T> last(Class<T> type) {
            MarketEvent found = null;
            for (MarketEvent event : events) {
                if (type.isInstance(event)) {
                    found = event;
                }
            }
            return Optional.ofNullable(type.cast(found));
        }

        public void clear() {
            events.clear();
        }
    }
}
