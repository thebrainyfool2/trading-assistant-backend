package com.tradingassistant.platform.api;

import com.tradingassistant.common.api.ApiResponse;
import com.tradingassistant.platform.api.dto.PortfolioDtos;
import com.tradingassistant.platform.executionapp.PortfolioQueryService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Open positions of the engine portfolio.
 */
@RestController
@RequestMapping(path = "/api/v1/positions", produces = MediaType.APPLICATION_JSON_VALUE)
public class PositionController {

    private final PortfolioQueryService portfolio;

    public PositionController(PortfolioQueryService portfolio) {
        this.portfolio = portfolio;
    }

    @GetMapping
    public ApiResponse<List<PortfolioDtos.PositionView>> positions() {
        return ApiResponse.success(portfolio.positions().stream()
                .map(PortfolioDtos.PositionView::from).toList());
    }
}
