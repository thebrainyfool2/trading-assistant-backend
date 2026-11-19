package com.tradingassistant.platform;

import com.tradingassistant.execution.domain.ExecutionDiagnostics;
import com.tradingassistant.execution.domain.ExecutionResult;
import com.tradingassistant.execution.domain.ExecutionStatus;
import com.tradingassistant.execution.domain.OrderSide;
import com.tradingassistant.execution.domain.OrderStatus;
import com.tradingassistant.execution.domain.OrderType;
import com.tradingassistant.execution.domain.PortfolioSnapshot;
import com.tradingassistant.execution.domain.Position;
import com.tradingassistant.execution.domain.PositionDirection;
import com.tradingassistant.execution.domain.PositionStatus;
import com.tradingassistant.execution.domain.TimeInForce;
import com.tradingassistant.execution.domain.TradeOrder;
import com.tradingassistant.platform.executionapp.OrderApplicationService;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.platform.history.OrderRecordDocument;
import com.tradingassistant.platform.history.PortfolioSnapshotDocument;
import com.tradingassistant.platform.notification.NotificationDocument;
import com.tradingassistant.platform.notification.NotificationType;
import com.tradingassistant.platform.security.AuthService;
import com.tradingassistant.platform.security.AuthenticatedUser;
import com.tradingassistant.platform.security.Role;
import com.tradingassistant.platform.user.UserDocument;
import com.tradingassistant.platform.user.UserStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Baseline immutable values shared by the platform tests.
 */
public final class PlatformTestFixtures {

    public static final Instant NOW = Instant.parse("2026-02-02T09:15:00Z");
    public static final InstrumentKey INSTRUMENT = new InstrumentKey(Segment.NSE_CASH, "2885");
    public static final String STRATEGY_ID = "EMA_TREND";
    public static final String USER_ID = "user-1";
    public static final String ENGINE_ORDER_ID = "ENG-1";

    private PlatformTestFixtures() {
    }

    public static UserDocument user(String id, String email, Role... roles) {
        Set<Role> granted = roles.length == 0 ? Set.of(Role.USER) : Set.of(roles);
        return new UserDocument(id, email, "Test User", "bcrypt-hash", granted,
                UserStatus.ACTIVE, NOW, NOW, null);
    }

    public static UserDocument user() {
        return user(USER_ID, "user@example.com");
    }

    public static AuthenticatedUser principal() {
        return new AuthenticatedUser(USER_ID, "user@example.com", Set.of(Role.USER));
    }

    public static AuthenticatedUser adminPrincipal() {
        return new AuthenticatedUser("admin-1", "admin@example.com",
                Set.of(Role.USER, Role.ADMIN));
    }

    public static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    public static PortfolioSnapshot portfolio() {
        return PortfolioSnapshot.empty(amount("100000"));
    }

    public static TradeOrder filledOrder() {
        return new TradeOrder(ENGINE_ORDER_ID, STRATEGY_ID, INSTRUMENT, OrderSide.BUY,
                OrderType.MARKET, amount("100"), amount("100"), Optional.empty(),
                Optional.empty(), Optional.of(amount("490")), Optional.empty(),
                TimeInForce.DAY, OrderStatus.FILLED, Optional.of(amount("500")), NOW,
                NOW, Optional.empty());
    }

    public static TradeOrder cancelledOrder() {
        return new TradeOrder(ENGINE_ORDER_ID, STRATEGY_ID, INSTRUMENT, OrderSide.BUY,
                OrderType.MARKET, amount("100"), BigDecimal.ZERO, Optional.empty(),
                Optional.empty(), Optional.of(amount("490")), Optional.empty(),
                TimeInForce.DAY, OrderStatus.CANCELLED, Optional.empty(), NOW, NOW,
                Optional.of("cancelled by user"));
    }

    public static Position openPosition() {
        return new Position("POS-1", STRATEGY_ID, INSTRUMENT, PositionDirection.LONG,
                amount("100"), BigDecimal.ZERO, amount("500"), Optional.of(amount("490")),
                Optional.empty(), BigDecimal.ZERO, amount("500"), PositionStatus.OPEN,
                NOW, NOW, Optional.empty());
    }

    public static ExecutionResult completedResult() {
        return new ExecutionResult(ExecutionStatus.COMPLETED, Optional.of(filledOrder()),
                Optional.of(openPosition()), portfolio(), List.of(), Optional.empty(),
                Optional.empty(), ExecutionDiagnostics.empty());
    }

    public static ExecutionResult rejectedResult() {
        return new ExecutionResult(ExecutionStatus.REJECTED, Optional.empty(),
                Optional.empty(), portfolio(), List.of(), Optional.empty(),
                Optional.of("risk limits breached"), ExecutionDiagnostics.empty());
    }

    public static OrderApplicationService.PlaceOrderCommand command() {
        return new OrderApplicationService.PlaceOrderCommand(STRATEGY_ID,
                Segment.NSE_CASH, "2885", OrderSide.BUY, OrderType.MARKET,
                Optional.empty(), Optional.empty(), Optional.empty(), TimeInForce.DAY);
    }

    public static OrderRecordDocument orderRecord(String userId) {
        return OrderRecordDocument.from(userId, STRATEGY_ID, Segment.NSE_CASH,
                "2885", OrderSide.BUY, OrderType.MARKET, amount("100"), null, null,
                null, TimeInForce.DAY, ExecutionStatus.COMPLETED, filledOrder(), null,
                NOW);
    }

    public static PortfolioSnapshotDocument portfolioSnapshot() {
        return PortfolioSnapshotDocument.capture("engine", portfolio(), NOW);
    }

    public static AuthService.AuthResult authResult() {
        return new AuthService.AuthResult(user(), "access-token", NOW.plusSeconds(900),
                "refresh-token", NOW.plusSeconds(86_400));
    }

    public static NotificationDocument notification(boolean read) {
        return new NotificationDocument("notification-1", USER_ID,
                NotificationType.LOGIN_ALERT, "New sign-in", "A new session signed in.",
                read, NOW, read ? NOW : null);
    }
}
