package com.tradingassistant.platform.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Background job configuration. Every job is a maintenance task; none of them
 * place orders or make trading decisions.
 *
 * @param enabled                  whether scheduling runs at all
 * @param tokenCleanupCron         cron for removing expired refresh tokens
 * @param portfolioSnapshotCron    cron for capturing the engine portfolio
 * @param notificationRetentionDays age after which read notifications are removed
 * @param tokenRetentionDays       age after which revoked tokens are removed
 */
@Validated
@ConfigurationProperties(prefix = "app.platform.scheduling")
public record SchedulingProperties(
        boolean enabled,
        @NotBlank String tokenCleanupCron,
        @NotBlank String portfolioSnapshotCron,
        @Min(1) int notificationRetentionDays,
        @Min(1) int tokenRetentionDays) {
}
