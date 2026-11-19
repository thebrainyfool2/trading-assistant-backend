package com.tradingassistant.platform.api;

import com.tradingassistant.common.api.ApiResponse;
import com.tradingassistant.platform.api.dto.OrderDtos;
import com.tradingassistant.platform.api.dto.PageResponse;
import com.tradingassistant.platform.executionapp.OrderApplicationService;
import com.tradingassistant.platform.history.OrderHistoryService;
import com.tradingassistant.platform.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Order placement, status and cancellation for the authenticated account.
 */
@RestController
@RequestMapping(path = "/api/v1/orders", produces = MediaType.APPLICATION_JSON_VALUE)
public class OrderController {

    private final OrderApplicationService orders;
    private final OrderHistoryService history;

    public OrderController(OrderApplicationService orders, OrderHistoryService history) {
        this.orders = orders;
        this.history = history;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<OrderDtos.OrderView>> place(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody OrderDtos.PlaceOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                OrderDtos.OrderView.from(orders.place(user.userId(),
                        request.toCommand()))));
    }

    @GetMapping
    public ApiResponse<PageResponse<OrderDtos.OrderView>> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(PageResponse.of(
                history.listForUser(user.userId(), page, size),
                OrderDtos.OrderView::from));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderDtos.OrderView> get(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id) {
        return ApiResponse.success(OrderDtos.OrderView.from(
                history.requireForUser(user.userId(), id)));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderDtos.OrderView> cancel(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id) {
        return ApiResponse.success(OrderDtos.OrderView.from(
                orders.cancel(user.userId(), id)));
    }
}
