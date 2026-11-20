package com.tradingassistant.platform.config;

import com.tradingassistant.platform.history.OrderRecordDocument;
import com.tradingassistant.platform.history.PortfolioSnapshotDocument;
import com.tradingassistant.platform.history.TradeRecordDocument;
import com.tradingassistant.monitoring.persistence.MonitoringAlertDocument;
import com.tradingassistant.monitoring.persistence.MonitoringHistoryDocument;
import com.tradingassistant.platform.notification.NotificationDocument;
import com.tradingassistant.platform.security.RefreshTokenDocument;
import com.tradingassistant.platform.settings.ApplicationSettingsDocument;
import com.tradingassistant.platform.user.UserDocument;
import com.tradingassistant.platform.watchlist.WatchlistDocument;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Creates the indexes the platform depends on.
 *
 * <p>Index creation is idempotent and best-effort. A deployment must still start
 * when the database is temporarily unreachable: the operational endpoints then
 * report the failure and requests that need the database answer with the
 * documented unavailable error instead of failing the whole process.</p>
 */
@Component
public class MongoIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MongoIndexInitializer.class);

    private final MongoTemplate mongoTemplate;

    public MongoIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            mongoTemplate.executeCommand(new Document("ping", 1));
        } catch (RuntimeException failure) {
            log.warn("MongoDB is not reachable at startup; index creation skipped: {}",
                    failure.toString());
            return;
        }
        ensure("users", () -> mongoTemplate.indexOps(UserDocument.class).ensureIndex(
                new Index().on("email", Sort.Direction.ASC).unique()));
        ensure("refresh_tokens.tokenHash", () -> mongoTemplate
                .indexOps(RefreshTokenDocument.class).ensureIndex(
                        new Index().on("tokenHash", Sort.Direction.ASC).unique()));
        ensure("refresh_tokens.expiresAt", () -> mongoTemplate
                .indexOps(RefreshTokenDocument.class).ensureIndex(
                        new Index().on("expiresAt", Sort.Direction.ASC)
                                .expire(0, TimeUnit.SECONDS)));
        ensure("watchlists.ownerName", () -> mongoTemplate
                .indexOps(WatchlistDocument.class).ensureIndex(
                        new Index().on("userId", Sort.Direction.ASC)
                                .on("name", Sort.Direction.ASC).unique()));
        ensure("orders.engineOrderId", () -> mongoTemplate
                .indexOps(OrderRecordDocument.class).ensureIndex(
                        new Index().on("engineOrderId", Sort.Direction.ASC)));
        ensure("trades.tradeKey", () -> mongoTemplate
                .indexOps(TradeRecordDocument.class).ensureIndex(
                        new Index().on("tradeKey", Sort.Direction.ASC).unique()));
        ensure("portfolio_snapshots.capturedAt", () -> mongoTemplate
                .indexOps(PortfolioSnapshotDocument.class).ensureIndex(
                        new Index().on("portfolioId", Sort.Direction.ASC)
                                .on("capturedAt", Sort.Direction.DESC)));
        ensure("notifications.userCreated", () -> mongoTemplate
                .indexOps(NotificationDocument.class).ensureIndex(
                        new Index().on("userId", Sort.Direction.ASC)
                                .on("createdAt", Sort.Direction.DESC)));
        ensure("application_settings", () -> mongoTemplate
                .indexOps(ApplicationSettingsDocument.class).ensureIndex(
                        new Index().on("updatedAt", Sort.Direction.DESC)));
        ensure("manual_trades.status", () -> mongoTemplate
                .indexOps(com.tradingassistant.monitoring.persistence.ManualTradeDocument.class)
                .ensureIndex(new Index().on("status", Sort.Direction.ASC)
                        .on("entryTimestamp", Sort.Direction.ASC)));
        ensure("monitoring_history.tradeTime", () -> mongoTemplate
                .indexOps(MonitoringHistoryDocument.class).ensureIndex(
                        new Index().on("tradeId", Sort.Direction.ASC)
                                .on("timestamp", Sort.Direction.DESC)));
        ensure("monitoring_alerts.time", () -> mongoTemplate
                .indexOps(MonitoringAlertDocument.class).ensureIndex(
                        new Index().on("timestamp", Sort.Direction.DESC)));
    }

    private static void ensure(String name, Runnable action) {
        try {
            action.run();
            log.debug("Ensured index {}", name);
        } catch (RuntimeException failure) {
            log.warn("Index {} could not be ensured: {}", name, failure.toString());
        }
    }
}
