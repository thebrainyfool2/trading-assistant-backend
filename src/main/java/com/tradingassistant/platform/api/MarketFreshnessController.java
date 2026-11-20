package com.tradingassistant.platform.api;

import com.tradingassistant.common.api.ApiResponse;
import com.tradingassistant.platform.api.dto.MarketFreshnessDtos;
import com.tradingassistant.platform.readmodel.MarketDataFreshnessService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Operational freshness view of instruments observed by the live pipeline.
 */
@RestController
@RequestMapping(path = "/api/v1/market", produces = MediaType.APPLICATION_JSON_VALUE)
public class MarketFreshnessController {

    private final MarketDataFreshnessService freshness;

    public MarketFreshnessController(MarketDataFreshnessService freshness) {
        this.freshness = freshness;
    }

    @GetMapping("/freshness")
    public ApiResponse<List<MarketFreshnessDtos.FreshnessView>> freshness() {
        return ApiResponse.success(freshness.snapshot().stream()
                .map(MarketFreshnessDtos.FreshnessView::from)
                .toList());
    }
}
