package com.tradingassistant.platform.api;

import com.tradingassistant.common.api.ApiResponse;
import com.tradingassistant.platform.api.dto.HistoryDtos;
import com.tradingassistant.platform.api.dto.OrderDtos;
import com.tradingassistant.platform.api.dto.PageResponse;
import com.tradingassistant.platform.api.dto.PortfolioDtos;
import com.tradingassistant.platform.history.OrderHistoryService;
import com.tradingassistant.platform.history.PortfolioSnapshotService;
import com.tradingassistant.platform.history.TradeHistoryService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only administrator views of all order, trade and portfolio history.
 */
@RestController
@RequestMapping(path = "/api/v1/admin", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final OrderHistoryService orders;
    private final TradeHistoryService trades;
    private final PortfolioSnapshotService snapshots;

    public AdminController(OrderHistoryService orders, TradeHistoryService trades,
                           PortfolioSnapshotService snapshots) {
        this.orders = orders;
        this.trades = trades;
        this.snapshots = snapshots;
    }

    @GetMapping("/orders")
    public ApiResponse<PageResponse<OrderDtos.OrderView>> orders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(PageResponse.of(orders.listAll(page, size),
                OrderDtos.OrderView::from));
    }

    @GetMapping("/history/trades")
    public ApiResponse<PageResponse<HistoryDtos.TradeView>> trades(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(PageResponse.of(trades.listAll(page, size),
                HistoryDtos.TradeView::from));
    }

    @GetMapping("/history/portfolio")
    public ApiResponse<PageResponse<PortfolioDtos.PortfolioSnapshotView>> portfolio(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(PageResponse.of(snapshots.history(page, size),
                PortfolioDtos.PortfolioSnapshotView::from));
    }
}
