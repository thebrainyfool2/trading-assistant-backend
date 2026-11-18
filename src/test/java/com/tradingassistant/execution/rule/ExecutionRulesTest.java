package com.tradingassistant.execution.rule;

import com.tradingassistant.execution.ExecutionTestFixtures;
import com.tradingassistant.execution.api.ExecutionRule;
import com.tradingassistant.execution.config.ExecutionConfig;
import com.tradingassistant.execution.domain.ExecutionRequest;
import com.tradingassistant.execution.domain.ExecutionRuleContext;
import com.tradingassistant.execution.domain.ExecutionRuleEvaluation;
import com.tradingassistant.execution.domain.ExecutionRuleStatus;
import com.tradingassistant.execution.domain.OrderRequest;
import com.tradingassistant.execution.domain.OrderSide;
import com.tradingassistant.execution.domain.OrderType;
import com.tradingassistant.execution.domain.PortfolioSnapshot;
import com.tradingassistant.execution.domain.TimeInForce;
import com.tradingassistant.execution.portfolio.PortfolioService;
import com.tradingassistant.execution.registry.BuiltInExecutionRules;
import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.intelligence.domain.MarketAssessment;
import com.tradingassistant.intelligence.domain.SessionAssessment;
import com.tradingassistant.intelligence.domain.SessionPhase;
import com.tradingassistant.market.domain.Segment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionRulesTest {

    private static ExecutionRuleContext context() {
        return ExecutionTestFixtures.ruleContext();
    }

    private static ExecutionRuleContext context(OrderRequest order) {
        ExecutionConfig config = ExecutionTestFixtures.config();
        return ExecutionTestFixtures.ruleContext(new ExecutionRequest(order,
                        ExecutionTestFixtures.assessment(), ExecutionTestFixtures.market()),
                PortfolioSnapshot.empty(config.referenceCapital()), config, false, true);
    }

    private static ExecutionRuleContext context(OrderRequest order, ExecutionConfig config,
                                                PortfolioSnapshot snapshot,
                                                boolean duplicate, boolean connected) {
        return ExecutionTestFixtures.ruleContext(new ExecutionRequest(order,
                ExecutionTestFixtures.assessment(), ExecutionTestFixtures.market()),
                snapshot, config, duplicate, connected);
    }

    private static ExecutionRequest requestWithSession(SessionPhase phase,
                                                       AssessmentStatus status) {
        MarketAssessment base = ExecutionTestFixtures.market();
        MarketAssessment market = new MarketAssessment(base.instrument(),
                base.interval(), base.timestamp(), base.status(), base.warmup(),
                base.confidence(), base.trend(), base.momentum(), base.volatility(),
                base.participation(), base.structure(),
                new SessionAssessment(phase, status, Optional.empty(), "test session"));
        return new ExecutionRequest(ExecutionTestFixtures.request(),
                ExecutionTestFixtures.assessment(), market);
    }

    private static OrderRequest order(String quantity, Optional<BigDecimal> limit,
                                      Optional<BigDecimal> trigger,
                                      Optional<BigDecimal> stop, OrderType type,
                                      OrderSide side) {
        return new OrderRequest(ExecutionTestFixtures.STRATEGY_ID,
                ExecutionTestFixtures.INSTRUMENT, side, type, new BigDecimal(quantity),
                limit, trigger, stop, Optional.empty(), TimeInForce.DAY,
                ExecutionTestFixtures.START);
    }

    private static OrderRequest marketOrder() {
        return order("100", Optional.empty(), Optional.empty(), Optional.of(
                ExecutionTestFixtures.STOP), OrderType.MARKET, OrderSide.BUY);
    }

    @Test
    void everyBuiltInRulePassesOnTheBaseline() {
        ExecutionRuleContext context = context();

        List<ExecutionRuleEvaluation> evaluations = BuiltInExecutionRules.all().stream()
                .map(rule -> rule.evaluate(context))
                .toList();

        assertThat(evaluations).hasSize(13);
        assertThat(evaluations).allSatisfy(evaluation ->
                assertThat(evaluation.status())
                        .as("%s: %s", evaluation.ruleId(), evaluation.reason())
                        .isEqualTo(ExecutionRuleStatus.PASSED));
    }

    @Test
    void validQuantityRejectsAQuantityAboveTheMaximum() {
        var evaluation = new ExecutionRules.ValidQuantity("VALID_QUANTITY").evaluate(
                context(order("20000", Optional.empty(), Optional.empty(),
                        Optional.of(ExecutionTestFixtures.STOP), OrderType.MARKET,
                        OrderSide.BUY)));

        assertThat(evaluation.status()).isEqualTo(ExecutionRuleStatus.FAILED);
        assertThat(evaluation.reason()).contains("maximum");
    }

    @Test
    void validPriceRequiresALimitForLimitOrders() {
        var evaluation = new ExecutionRules.ValidPrice("VALID_PRICE").evaluate(
                context(order("100", Optional.empty(), Optional.empty(),
                        Optional.of(ExecutionTestFixtures.STOP), OrderType.LIMIT,
                        OrderSide.BUY)));

        assertThat(evaluation.status()).isEqualTo(ExecutionRuleStatus.FAILED);
        assertThat(evaluation.reason()).contains("limit price");
    }

    @Test
    void validPriceAcceptsAMarketOrder() {
        var evaluation = new ExecutionRules.ValidPrice("VALID_PRICE").evaluate(
                context(marketOrder()));

        assertThat(evaluation.passed()).isTrue();
        assertThat(evaluation.metadata()).containsKey("referencePrice");
    }

    @Test
    void validStopRejectsAMissingStop() {
        var evaluation = new ExecutionRules.ValidStop("VALID_STOP").evaluate(
                context(order("100", Optional.empty(), Optional.empty(),
                        Optional.empty(), OrderType.MARKET, OrderSide.BUY)));

        assertThat(evaluation.status()).isEqualTo(ExecutionRuleStatus.FAILED);
        assertThat(evaluation.reason()).contains("missing");
    }

    @Test
    void validStopRejectsAStopOnTheWrongSide() {
        var evaluation = new ExecutionRules.ValidStop("VALID_STOP").evaluate(
                context(order("100", Optional.empty(), Optional.empty(),
                        Optional.of(new BigDecimal("510")), OrderType.MARKET,
                        OrderSide.BUY)));

        assertThat(evaluation.status()).isEqualTo(ExecutionRuleStatus.FAILED);
        assertThat(evaluation.metadata()).containsEntry("stopPrice", "510");
    }

    @Test
    void validStopAcceptsAShortStopAboveTheReference() {
        var evaluation = new ExecutionRules.ValidStop("VALID_STOP").evaluate(
                context(order("100", Optional.empty(), Optional.empty(),
                        Optional.of(new BigDecimal("510")), OrderType.MARKET,
                        OrderSide.SELL)));

        assertThat(evaluation.passed()).isTrue();
    }

    @Test
    void validTargetRejectsATargetBelowTheReferenceForALong() {
        var evaluation = new ExecutionRules.ValidTarget("VALID_TARGET").evaluate(
                context(new OrderRequest(ExecutionTestFixtures.STRATEGY_ID,
                        ExecutionTestFixtures.INSTRUMENT, OrderSide.BUY, OrderType.MARKET,
                        new BigDecimal("100"), Optional.empty(), Optional.empty(),
                        Optional.of(ExecutionTestFixtures.STOP),
                        Optional.of(new BigDecimal("480")), TimeInForce.DAY,
                        ExecutionTestFixtures.START)));

        assertThat(evaluation.status()).isEqualTo(ExecutionRuleStatus.FAILED);
    }

    @Test
    void validTargetPassesWithoutATarget() {
        var evaluation = new ExecutionRules.ValidTarget("VALID_TARGET").evaluate(
                context(marketOrder()));

        assertThat(evaluation.passed()).isTrue();
        assertThat(evaluation.metadata()).containsEntry("target", "absent");
    }

    @Test
    void marketOpenAcceptsAReadyRegularSession() {
        var evaluation = new ExecutionRules.MarketOpen("MARKET_OPEN").evaluate(
                ExecutionTestFixtures.ruleContext(requestWithSession(SessionPhase.REGULAR,
                                AssessmentStatus.READY),
                        PortfolioSnapshot.empty(ExecutionTestFixtures.config()
                                .referenceCapital()),
                        ExecutionTestFixtures.config(), false, true));

        assertThat(evaluation.passed()).isTrue();
    }

    @Test
    void marketOpenRejectsAClosedSession() {
        var evaluation = new ExecutionRules.MarketOpen("MARKET_OPEN").evaluate(
                ExecutionTestFixtures.ruleContext(requestWithSession(SessionPhase.AFTER_MARKET,
                                AssessmentStatus.READY),
                        PortfolioSnapshot.empty(ExecutionTestFixtures.config()
                                .referenceCapital()),
                        ExecutionTestFixtures.config(), false, true));

        assertThat(evaluation.status()).isEqualTo(ExecutionRuleStatus.FAILED);
        assertThat(evaluation.metadata()).containsEntry("phase", "AFTER_MARKET");
    }

    @Test
    void marketOpenIsUndecidableWithoutASessionContext() {
        var evaluation = new ExecutionRules.MarketOpen("MARKET_OPEN").evaluate(
                ExecutionTestFixtures.ruleContext(requestWithSession(SessionPhase.UNKNOWN,
                                AssessmentStatus.INSUFFICIENT_DATA),
                        PortfolioSnapshot.empty(ExecutionTestFixtures.config()
                                .referenceCapital()),
                        ExecutionTestFixtures.config(), false, true));

        assertThat(evaluation.status())
                .isEqualTo(ExecutionRuleStatus.INSUFFICIENT_DATA);
    }

    @Test
    void marketOpenIsSkippedWhenPolicyDoesNotRequireIt() {
        ExecutionConfig config = ExecutionConfig.builder().requireMarketOpen(false).build();
        var evaluation = new ExecutionRules.MarketOpen("MARKET_OPEN").evaluate(
                context(marketOrder(), config, PortfolioSnapshot.empty(
                        config.referenceCapital()), false, true));

        assertThat(evaluation.passed()).isTrue();
        assertThat(evaluation.metadata()).containsEntry("required", "false");
    }

    @Test
    void brokerConnectedRejectsADisconnectedBroker() {
        var evaluation = new ExecutionRules.BrokerConnected("BROKER_CONNECTED").evaluate(
                context(marketOrder(), ExecutionTestFixtures.config(),
                        PortfolioSnapshot.empty(new BigDecimal("100000")), false, false));

        assertThat(evaluation.status()).isEqualTo(ExecutionRuleStatus.FAILED);
        assertThat(evaluation.reason()).contains("not connected");
    }

    @Test
    void accountReadyRejectsAnAccountWithoutCapital() {
        var evaluation = new ExecutionRules.AccountReady("ACCOUNT_READY").evaluate(
                context(marketOrder(), ExecutionTestFixtures.config(),
                        PortfolioSnapshot.empty(BigDecimal.ZERO), false, true));

        assertThat(evaluation.status()).isEqualTo(ExecutionRuleStatus.FAILED);
        assertThat(evaluation.reason()).contains("capital");
    }

    @Test
    void accountReadyRejectsAnAccountFlaggedNotReady() {
        ExecutionRuleContext context = new ExecutionRuleContext(
                new ExecutionRequest(marketOrder(), ExecutionTestFixtures.assessment(),
                        ExecutionTestFixtures.market()),
                PortfolioSnapshot.empty(new BigDecimal("100000")),
                ExecutionTestFixtures.config(), false, true, false,
                new BigDecimal("500"));
        var evaluation = new ExecutionRules.AccountReady("ACCOUNT_READY")
                .evaluate(context);

        assertThat(evaluation.status()).isEqualTo(ExecutionRuleStatus.FAILED);
        assertThat(evaluation.reason()).contains("not ready");
    }

    @Test
    void sufficientMarginRejectsAnOrderBeyondAvailableCapital() {
        var evaluation = new ExecutionRules.SufficientMargin("SUFFICIENT_MARGIN").evaluate(
                context(marketOrder(), ExecutionTestFixtures.config(),
                        PortfolioSnapshot.empty(new BigDecimal("1000")), false, true));

        assertThat(evaluation.status()).isEqualTo(ExecutionRuleStatus.FAILED);
        assertThat(evaluation.metadata()).containsEntry("requiredMargin", "10000");
    }

    @Test
    void sufficientMarginIsUndecidableWithoutAvailableCapital() {
        var evaluation = new ExecutionRules.SufficientMargin("SUFFICIENT_MARGIN").evaluate(
                context(marketOrder(), ExecutionTestFixtures.config(),
                        PortfolioSnapshot.empty(BigDecimal.ZERO), false, true));

        assertThat(evaluation.status())
                .isEqualTo(ExecutionRuleStatus.INSUFFICIENT_DATA);
    }

    @Test
    void orderNotDuplicateRejectsACoexistingOrder() {
        var evaluation = new ExecutionRules.OrderNotDuplicate("ORDER_NOT_DUPLICATE")
                .evaluate(context(marketOrder(), ExecutionTestFixtures.config(),
                        PortfolioSnapshot.empty(new BigDecimal("100000")), true, true));

        assertThat(evaluation.status()).isEqualTo(ExecutionRuleStatus.FAILED);
        assertThat(evaluation.metadata()).containsEntry("side", "BUY");
    }

    @Test
    void positionLimitRejectsAnOpeningOrderAtTheLimitAndAllowsAClosingOne() {
        ExecutionConfig config = ExecutionConfig.builder().maxOpenPositions(1).build();
        PortfolioService portfolio = new PortfolioService(config);
        portfolio.applyFill(
                com.tradingassistant.execution.domain.TradeOrder.created(
                        ExecutionTestFixtures.request(), "ORD-1",
                        ExecutionTestFixtures.START),
                new com.tradingassistant.execution.domain.OrderExecution("EX-1", "ORD-1",
                        new BigDecimal("100"), new BigDecimal("500"),
                        ExecutionTestFixtures.START));
        PortfolioSnapshot snapshot = portfolio.snapshot();

        var opening = new ExecutionRules.PositionLimit("POSITION_LIMIT").evaluate(
                context(marketOrder(), config, snapshot, false, true));
        var closing = new ExecutionRules.PositionLimit("POSITION_LIMIT").evaluate(
                context(order("100", Optional.empty(), Optional.empty(),
                        Optional.of(new BigDecimal("510")), OrderType.MARKET,
                        OrderSide.SELL), config, snapshot, false, true));

        assertThat(opening.status()).isEqualTo(ExecutionRuleStatus.FAILED);
        assertThat(closing.passed()).isTrue();
        assertThat(closing.metadata()).containsEntry("closing", "true");
    }

    @Test
    void leverageLimitRejectsAnOrderAboveTheCeiling() {
        ExecutionConfig config = ExecutionConfig.builder()
                .maxLeverage(new BigDecimal("0.4")).build();
        var evaluation = new ExecutionRules.LeverageLimit("LEVERAGE_LIMIT").evaluate(
                context(marketOrder(), config, PortfolioSnapshot.empty(
                        config.referenceCapital()), false, true));

        assertThat(evaluation.status()).isEqualTo(ExecutionRuleStatus.FAILED);
        assertThat(evaluation.metadata()).containsEntry("maximum", "0.4");
    }

    @Test
    void leverageLimitIsUndecidableWithoutCapital() {
        var evaluation = new ExecutionRules.LeverageLimit("LEVERAGE_LIMIT").evaluate(
                context(marketOrder(), ExecutionTestFixtures.config(),
                        PortfolioSnapshot.empty(BigDecimal.ZERO), false, true));

        assertThat(evaluation.status())
                .isEqualTo(ExecutionRuleStatus.INSUFFICIENT_DATA);
    }

    @Test
    void validProductRejectsASegmentOutsideThePolicy() {
        ExecutionConfig config = ExecutionConfig.builder()
                .allowedSegments(Set.of(Segment.NSE_FO)).build();
        var evaluation = new ExecutionRules.ValidProduct("VALID_PRODUCT").evaluate(
                context(marketOrder(), config, PortfolioSnapshot.empty(
                        config.referenceCapital()), false, true));

        assertThat(evaluation.status()).isEqualTo(ExecutionRuleStatus.FAILED);
        assertThat(evaluation.metadata()).containsEntry("segment", "NSE_CASH");
    }

    @Test
    void validOrderTypeRejectsADisallowedType() {
        ExecutionConfig config = ExecutionConfig.builder()
                .allowedOrderTypes(Set.of(OrderType.MARKET)).build();
        var evaluation = new ExecutionRules.ValidOrderType("VALID_ORDER_TYPE").evaluate(
                context(order("100", Optional.of(new BigDecimal("500")),
                                Optional.empty(), Optional.of(ExecutionTestFixtures.STOP),
                                OrderType.LIMIT, OrderSide.BUY),
                        config, PortfolioSnapshot.empty(config.referenceCapital()), false,
                        true));

        assertThat(evaluation.status()).isEqualTo(ExecutionRuleStatus.FAILED);
    }

    @Test
    void validOrderTypeRequiresATriggerForStopOrders() {
        var evaluation = new ExecutionRules.ValidOrderType("VALID_ORDER_TYPE").evaluate(
                context(order("100", Optional.empty(), Optional.empty(),
                        Optional.of(ExecutionTestFixtures.STOP), OrderType.STOP,
                        OrderSide.BUY)));

        assertThat(evaluation.status()).isEqualTo(ExecutionRuleStatus.FAILED);
        assertThat(evaluation.reason()).contains("trigger");
    }

    @Test
    void validOrderTypeRequiresALimitForStopLimitOrder() {
        var evaluation = new ExecutionRules.ValidOrderType("VALID_ORDER_TYPE").evaluate(
                context(order("100", Optional.empty(),
                        Optional.of(new BigDecimal("498")),
                        Optional.of(ExecutionTestFixtures.STOP), OrderType.STOP_LIMIT,
                        OrderSide.BUY)));

        assertThat(evaluation.status()).isEqualTo(ExecutionRuleStatus.FAILED);
        assertThat(evaluation.reason()).contains("limit");
    }

    @Test
    void ruleIdentifiersMustNotBeBlank() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> new ExecutionRules.ValidQuantity(" ")))
                .isInstanceOf(com.tradingassistant.execution.exception
                        .ExecutionValidationException.class);
    }

    @Test
    void rulesRejectANullContext() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> new ExecutionRules.ValidQuantity("VALID_QUANTITY").evaluate(null)))
                .isInstanceOf(com.tradingassistant.execution.exception
                        .ExecutionValidationException.class);
    }

    @Test
    void catalogIdentifiersAreUniqueAndComplete() {
        List<String> ids = BuiltInExecutionRules.all().stream().map(ExecutionRule::id)
                .toList();

        assertThat(ids).hasSize(13).doesNotHaveDuplicates();
        assertThat(ids).containsExactly("VALID_QUANTITY", "VALID_PRICE", "VALID_STOP",
                "VALID_TARGET", "MARKET_OPEN", "BROKER_CONNECTED", "ACCOUNT_READY",
                "SUFFICIENT_MARGIN", "ORDER_NOT_DUPLICATE", "POSITION_LIMIT",
                "LEVERAGE_LIMIT", "VALID_PRODUCT", "VALID_ORDER_TYPE");
    }
}
