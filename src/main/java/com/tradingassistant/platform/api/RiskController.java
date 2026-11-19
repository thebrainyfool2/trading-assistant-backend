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
 * Risk assessments and trade qualifications produced by the pipeline.
 */
@RestController
@RequestMapping(path = "/api/v1/risk", produces = MediaType.APPLICATION_JSON_VALUE)
public class RiskController {

    private final AnalysisQueryService analysis;

    public RiskController(AnalysisQueryService analysis) {
        this.analysis = analysis;
    }

    @GetMapping("/assessments")
    public ApiResponse<List<MarketDtos.RiskAssessmentView>> assessments(
            @RequestParam(defaultValue = "25") Integer limit) {
        return ApiResponse.success(analysis.risks(limit).stream()
                .map(MarketDtos.RiskAssessmentView::from).toList());
    }

    @GetMapping("/qualifications")
    public ApiResponse<List<MarketDtos.QualificationView>> qualifications(
            @RequestParam(defaultValue = "25") Integer limit) {
        return ApiResponse.success(analysis.qualifications(limit).stream()
                .map(MarketDtos.QualificationView::from).toList());
    }
}
