package com.tradingassistant.platform.api;

import com.tradingassistant.common.api.ApiResponse;
import com.tradingassistant.platform.api.dto.HistoryDtos;
import com.tradingassistant.platform.api.dto.OrderDtos;
import com.tradingassistant.platform.api.dto.PageResponse;
import com.tradingassistant.platform.api.dto.PortfolioDtos;
import com.tradingassistant.platform.history.OrderHistoryService;
import com.tradingassistant.platform.history.PortfolioSnapshotService;
import com.tradingassistant.platform.history.TradeHistoryService;
import com.tradingassistant.platform.security.AuthenticatedUser;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account-scoped order, trade and portfolio history.
 */
@RestController
@RequestMapping(path = "/api/v1/history", produces = MediaType.APPLICATION_JSON_VALUE)
public class HistoryController {

    private final OrderHistoryService orders;
    private final TradeHistoryService trades;
    private final PortfolioSnapshotService snapshots;

    public HistoryController(OrderHistoryService orders, TradeHistoryService trades,
                             PortfolioSnapshotService snapshots) {
        this.orders = orders;
        this.trades = trades;
        this.snapshots = snapshots;
    }

    @GetMapping("/orders")
    public ApiResponse<PageResponse<OrderDtos.OrderView>> orders(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(PageResponse.of(
                orders.listForUser(user.userId(), page, size),
                OrderDtos.OrderView::from));
    }

    @GetMapping("/trades")
    public ApiResponse<PageResponse<HistoryDtos.TradeView>> trades(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(PageResponse.of(
                trades.listForUser(user.userId(), page, size),
                HistoryDtos.TradeView::from));
    }

    @GetMapping("/portfolio")
    public ApiResponse<PageResponse<PortfolioDtos.PortfolioSnapshotView>> portfolio(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(PageResponse.of(snapshots.history(page, size),
                PortfolioDtos.PortfolioSnapshotView::from));
    }
}
