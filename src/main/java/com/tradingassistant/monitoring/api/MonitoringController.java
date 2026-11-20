package com.tradingassistant.monitoring.api;

import com.tradingassistant.common.api.ApiResponse;
import com.tradingassistant.monitoring.service.MonitoringQueryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only monitoring views for active manual trades. There is deliberately no
 * endpoint that records, modifies, closes or executes a trade.
 */
@RestController
@ConditionalOnProperty(prefix = "app.broker.angel-one", name = "enabled",
        havingValue = "true")
@RequestMapping(path = "/api/v1/monitoring",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class MonitoringController {

    private final MonitoringQueryService queries;

    public MonitoringController(MonitoringQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/active")
    public ApiResponse<List<MonitoringDtos.ActiveView>> active() {
        return ApiResponse.success(queries.active().stream()
                .map(MonitoringDtos.ActiveView::from).toList());
    }

    @GetMapping("/history")
    public ApiResponse<List<MonitoringDtos.HistoryView>> history(
            @RequestParam(required = false) String tradeId,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.success(queries.history(tradeId, limit).stream()
                .map(MonitoringDtos.HistoryView::from).toList());
    }

    @GetMapping("/alerts")
    public ApiResponse<List<MonitoringDtos.AlertView>> alerts(
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.success(queries.alerts(limit).stream()
                .map(MonitoringDtos.AlertView::from).toList());
    }

    @GetMapping("/{tradeId}")
    public ApiResponse<MonitoringDtos.ActiveView> trade(@PathVariable String tradeId) {
        return ApiResponse.success(MonitoringDtos.ActiveView.from(
                queries.require(tradeId)));
    }
}