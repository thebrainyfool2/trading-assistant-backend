package com.tradingassistant.platform.trade;

import com.tradingassistant.common.api.ErrorCode;
import com.tradingassistant.platform.error.PlatformException;

import java.math.BigDecimal;

/**
 * Policy for the deterministic trade plan shown beside a recommendation.
 *
 * <p>The plan uses the existing risk engine's ATR stop and the existing ATM
 * Greeks as a linear option-premium approximation. It does not forecast a price.
 * The fallback delta is used only when the options assessment has no ATM Greek,
 * and the fallback percentages are used only when no recommendation plan is
 * available at manual registration time.</p>
 */
public record TradePlanConfig(
        BigDecimal deltaFallback,
        BigDecimal fallbackTargetPercent,
        BigDecimal fallbackStopPercent,
        long premiumRefreshMillis,
        boolean premiumAlertsEnabled) {

    public TradePlanConfig {
        deltaFallback = percentage(deltaFallback, "deltaFallback", false);
        if (deltaFallback.signum() <= 0) {
            throw new PlatformException(ErrorCode.VALIDATION_FAILED,
                    "app.trade-plan.delta-fallback must be greater than zero.");
        }
        fallbackTargetPercent = percentage(fallbackTargetPercent,
                "fallbackTargetPercent", true);
        fallbackStopPercent = percentage(fallbackStopPercent,
                "fallbackStopPercent", true);
        if (fallbackStopPercent.signum() <= 0
                || fallbackStopPercent.compareTo(BigDecimal.valueOf(100)) >= 0) {
            throw new PlatformException(ErrorCode.VALIDATION_FAILED,
                    "app.trade-plan.fallback-stop-percent must be between 0 and 100.");
        }
        if (premiumRefreshMillis <= 0) {
            throw new PlatformException(ErrorCode.VALIDATION_FAILED,
                    "app.trade-plan.premium-refresh-ms must be positive.");
        }
    }

    public static TradePlanConfig defaults() {
        return new TradePlanConfig(new BigDecimal("0.50"), new BigDecimal("40"),
                new BigDecimal("20"), 30_000L, true);
    }

    private static BigDecimal percentage(BigDecimal value, String field,
                                         boolean allowZero) {
        if (value == null) {
            throw new PlatformException(ErrorCode.VALIDATION_FAILED,
                    "app.trade-plan." + field + " must be configured.");
        }
        if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new PlatformException(ErrorCode.VALIDATION_FAILED,
                    "app.trade-plan." + field + " must be between 0 and 100.");
        }
        if (!allowZero && value.signum() == 0) {
            throw new PlatformException(ErrorCode.VALIDATION_FAILED,
                    "app.trade-plan." + field + " must be greater than zero.");
        }
        return value;
    }
}
