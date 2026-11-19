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
 * Options descriptions published by the analytics engine.
 */
@RestController
@RequestMapping(path = "/api/v1/options", produces = MediaType.APPLICATION_JSON_VALUE)
public class OptionsController {

    private final AnalysisQueryService analysis;

    public OptionsController(AnalysisQueryService analysis) {
        this.analysis = analysis;
    }

    @GetMapping("/assessments")
    public ApiResponse<List<MarketDtos.OptionsAssessmentView>> assessments(
            @RequestParam(defaultValue = "25") Integer limit) {
        return ApiResponse.success(analysis.options(limit).stream()
                .map(MarketDtos.OptionsAssessmentView::from).toList());
    }
}
