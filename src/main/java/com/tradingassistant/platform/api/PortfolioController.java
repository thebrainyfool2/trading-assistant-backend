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
 * Live or last-persisted portfolio state. The response names its source.
 */
@RestController
@RequestMapping(path = "/api/v1/portfolio", produces = MediaType.APPLICATION_JSON_VALUE)
public class PortfolioController {

    private final PortfolioQueryService portfolio;

    public PortfolioController(PortfolioQueryService portfolio) {
        this.portfolio = portfolio;
    }

    @GetMapping
    public ApiResponse<PortfolioDtos.PortfolioView> current() {
        return ApiResponse.success(PortfolioDtos.PortfolioView.from(portfolio.current()));
    }

    @GetMapping("/positions")
    public ApiResponse<List<PortfolioDtos.PositionView>> positions() {
        return ApiResponse.success(portfolio.positions().stream()
                .map(PortfolioDtos.PositionView::from).toList());
    }

    @GetMapping("/summary")
    public ApiResponse<PortfolioDtos.PortfolioView> summary() {
        return current();
    }
}
