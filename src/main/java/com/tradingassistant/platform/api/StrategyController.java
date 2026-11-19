package com.tradingassistant.platform.api;

import com.tradingassistant.common.api.ApiResponse;
import com.tradingassistant.platform.api.dto.MarketDtos;
import com.tradingassistant.platform.readmodel.AnalysisQueryService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Strategy definitions and the latest evaluations.
 */
@RestController
@RequestMapping(path = "/api/v1/strategy", produces = MediaType.APPLICATION_JSON_VALUE)
public class StrategyController {

    private final AnalysisQueryService analysis;

    public StrategyController(AnalysisQueryService analysis) {
        this.analysis = analysis;
    }

    @GetMapping("/evaluations")
    public ApiResponse<List<MarketDtos.StrategyEvaluationView>> evaluations(
            @RequestParam(defaultValue = "25") Integer limit) {
        return ApiResponse.success(analysis.strategies(limit).stream()
                .map(MarketDtos.StrategyEvaluationView::from).toList());
    }

    @GetMapping("/definitions")
    public ApiResponse<List<MarketDtos.StrategyDefinitionView>> definitions() {
        return ApiResponse.success(analysis.definitions().stream()
                .map(MarketDtos.StrategyDefinitionView::from).toList());
    }
}
