package com.tradingassistant.platform.trade;

import com.tradingassistant.common.api.ApiResponse;
import com.tradingassistant.monitoring.api.MonitoringDtos;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The owner records a fill after executing it manually. This endpoint never
 * places an order; it only starts local monitoring against existing evidence.
 */
@RestController
@ConditionalOnProperty(prefix = "app.broker.angel-one", name = "enabled",
        havingValue = "true")
@RequestMapping(path = "/api/v1/monitoring/trades",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class ManualTradeController {

    private final ManualTradeCommandService commands;

    public ManualTradeController(ManualTradeCommandService commands) {
        this.commands = commands;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<MonitoringDtos.TradeView>> register(
            @Valid @RequestBody ManualTradeDtos.CreateTradeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                commands.register(request)));
    }

    @PostMapping(path = "/{tradeId}/close",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<MonitoringDtos.TradeView> close(
            @PathVariable String tradeId,
            @RequestBody(required = false) ManualTradeDtos.CloseTradeRequest request) {
        return ApiResponse.success(commands.close(tradeId,
                request == null ? null : request.reason()));
    }
}
