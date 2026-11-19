package com.tradingassistant.platform.jobs;

import com.tradingassistant.execution.portfolio.PortfolioService;
import com.tradingassistant.platform.config.SchedulingProperties;
import com.tradingassistant.platform.history.PortfolioSnapshotService;
import com.tradingassistant.platform.notification.NotificationService;
import com.tradingassistant.platform.readmodel.PipelineSnapshotStore;
import com.tradingassistant.platform.security.RefreshTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Maintenance jobs: token cleanup, portfolio snapshots, notification retention
 * and read-model trimming.
 *
 * <p>Every job is idempotent and fails soft. None of them executes a trade,
 * evaluates a strategy or changes engine state.</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.platform.scheduling", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class MaintenanceJobs {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceJobs.class);
    private static final Duration READ_MODEL_RETENTION = Duration.ofDays(7);

    private final RefreshTokenService refreshTokens;
    private final PortfolioSnapshotService snapshots;
    private final ObjectProvider<PortfolioService> portfolioService;
    private final NotificationService notifications;
    private final PipelineSnapshotStore readModel;
    private final SchedulingProperties properties;
    private final Clock clock;

    public MaintenanceJobs(RefreshTokenService refreshTokens,
                           PortfolioSnapshotService snapshots,
                           ObjectProvider<PortfolioService> portfolioService,
                           NotificationService notifications,
                           PipelineSnapshotStore readModel,
                           SchedulingProperties properties,
                           Clock clock) {
        this.refreshTokens = refreshTokens;
        this.snapshots = snapshots;
        this.portfolioService = portfolioService;
        this.notifications = notifications;
        this.readModel = readModel;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.platform.scheduling.token-cleanup-cron:0 15 * * * *}",
            zone = "UTC")
    public void cleanRefreshTokens() {
        try {
            long removed = refreshTokens.cleanup(
                    Duration.ofDays(properties.tokenRetentionDays()));
            if (removed > 0) {
                log.info("Removed {} expired or revoked refresh tokens", removed);
            }
        } catch (RuntimeException failure) {
            log.warn("Refresh-token cleanup failed: {}", failure.toString());
        }
    }

    @Scheduled(cron = "${app.platform.scheduling.portfolio-snapshot-cron:0 */15 * * * *}",
            zone = "UTC")
    public void capturePortfolioSnapshot() {
        PortfolioService service = portfolioService.getIfAvailable();
        if (service == null) {
            return;
        }
        try {
            snapshots.capture(service.snapshot());
        } catch (RuntimeException failure) {
            log.warn("Portfolio snapshot job failed: {}", failure.toString());
        }
    }

    @Scheduled(cron = "${app.platform.scheduling.notification-cleanup-cron:0 30 2 * * *}",
            zone = "UTC")
    public void purgeNotifications() {
        notifications.purgeReadBefore(
                Duration.ofDays(properties.notificationRetentionDays()));
    }

    @Scheduled(cron = "${app.platform.scheduling.read-model-trim-cron:0 45 2 * * *}",
            zone = "UTC")
    public void trimReadModel() {
        Instant cutoff = clock.instant().minus(READ_MODEL_RETENTION);
        int removed = readModel.trim(cutoff);
        if (removed > 0) {
            log.info("Trimmed {} stale read-model entries", removed);
        }
    }
}
