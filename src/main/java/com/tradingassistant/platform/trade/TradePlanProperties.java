package com.tradingassistant.platform.trade;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/** External trade-plan and option-premium monitoring policy. */
@ConfigurationProperties(prefix = "app.trade-plan")
public record TradePlanProperties(
        BigDecimal deltaFallback,
        BigDecimal fallbackTargetPercent,
        BigDecimal fallbackStopPercent,
        Long premiumRefreshMs,
        Boolean premiumAlertsEnabled) {

    public TradePlanConfig toConfig() {
        if (premiumRefreshMs == null) {
            throw new com.tradingassistant.platform.error.PlatformException(
                    com.tradingassistant.common.api.ErrorCode.VALIDATION_FAILED,
                    "app.trade-plan.premium-refresh-ms must be configured.");
        }
        if (premiumAlertsEnabled == null) {
            throw new com.tradingassistant.platform.error.PlatformException(
                    com.tradingassistant.common.api.ErrorCode.VALIDATION_FAILED,
                    "app.trade-plan.premium-alerts-enabled must be configured.");
        }
        return new TradePlanConfig(deltaFallback, fallbackTargetPercent,
                fallbackStopPercent, premiumRefreshMs, premiumAlertsEnabled);
    }
}
