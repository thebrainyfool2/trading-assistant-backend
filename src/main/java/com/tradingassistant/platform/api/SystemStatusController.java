package com.tradingassistant.platform.api;

import com.tradingassistant.common.api.ApiResponse;
import com.tradingassistant.platform.readmodel.PipelineSnapshotStore;
import com.tradingassistant.platform.readmodel.AnalysisQueryService;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Operational status beyond the aggregate health endpoint.
 *
 * <p>It reports database reachability, engine availability and read-model size.
 * It never exposes configuration values, credentials or broker identity.</p>
 */
@RestController
@RequestMapping(path = "/api/v1/system", produces = MediaType.APPLICATION_JSON_VALUE)
public class SystemStatusController {

    private static final Logger log = LoggerFactory.getLogger(SystemStatusController.class);

    private final MongoTemplate mongoTemplate;
    private final AnalysisQueryService analysis;
    private final PipelineSnapshotStore readModel;
    private final ObjectProvider<BuildProperties> buildProperties;
    private final Clock clock;

    public SystemStatusController(MongoTemplate mongoTemplate, AnalysisQueryService analysis,
                                  PipelineSnapshotStore readModel,
                                  ObjectProvider<BuildProperties> buildProperties,
                                  Clock clock) {
        this.mongoTemplate = mongoTemplate;
        this.analysis = analysis;
        this.readModel = readModel;
        this.buildProperties = buildProperties;
        this.clock = clock;
    }

    @GetMapping("/status")
    public ApiResponse<StatusView> status() {
        boolean databaseUp = databaseReachable();
        String database = databaseUp ? "UP" : "DOWN";
        String engine = analysis.engineAvailable() ? "AVAILABLE" : "DISABLED";
        String status = databaseUp ? "UP" : "DEGRADED";
        return ApiResponse.success(new StatusView(clock.instant(), status, database,
                engine, version(), readModel.size(), uptimeSeconds()));
    }

    private boolean databaseReachable() {
        try {
            mongoTemplate.executeCommand(new Document("ping", 1));
            return true;
        } catch (RuntimeException failure) {
            log.warn("Database ping failed: {}", failure.toString());
            return false;
        }
    }

    private String version() {
        return Optional.ofNullable(buildProperties.getIfAvailable())
                .map(BuildProperties::getVersion)
                .orElse("unknown");
    }

    private static long uptimeSeconds() {
        return ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
    }

    /**
     * Safe operational status response.
     */
    public record StatusView(Instant timestamp, String status, String database,
                             String engine, String version, int readModelEntries,
                             long uptimeSeconds) {
    }
}
