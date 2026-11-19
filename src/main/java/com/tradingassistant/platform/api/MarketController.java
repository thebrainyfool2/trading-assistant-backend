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
 * Market descriptions published by the intelligence engine.
 */
@RestController
@RequestMapping(path = "/api/v1/market", produces = MediaType.APPLICATION_JSON_VALUE)
public class MarketController {

    private final AnalysisQueryService analysis;

    public MarketController(AnalysisQueryService analysis) {
        this.analysis = analysis;
    }

    @GetMapping("/overview")
    public ApiResponse<MarketDtos.MarketOverviewView> overview(
            @RequestParam(defaultValue = "25") Integer limit) {
        List<MarketDtos.MarketAssessmentView> markets = analysis.markets(limit).stream()
                .map(MarketDtos.MarketAssessmentView::from).toList();
        return ApiResponse.success(new MarketDtos.MarketOverviewView(analysis.now(),
                analysis.engineAvailable(), analysis.readModelEntries(), markets,
                analysis.options(limit).stream()
                        .map(MarketDtos.OptionsAssessmentView::from).toList(),
                analysis.strategies(limit).stream()
                        .map(MarketDtos.StrategyEvaluationView::from).toList(),
                analysis.risks(limit).stream()
                        .map(MarketDtos.RiskAssessmentView::from).toList(),
                analysis.qualifications(limit).stream()
                        .map(MarketDtos.QualificationView::from).toList()));
    }

    @GetMapping("/assessments")
    public ApiResponse<List<MarketDtos.MarketAssessmentView>> assessments(
            @RequestParam(defaultValue = "25") Integer limit) {
        return ApiResponse.success(analysis.markets(limit).stream()
                .map(MarketDtos.MarketAssessmentView::from).toList());
    }
}
