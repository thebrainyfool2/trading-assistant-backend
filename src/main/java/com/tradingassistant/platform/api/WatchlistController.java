package com.tradingassistant.platform.api;

import com.tradingassistant.common.api.ApiResponse;
import com.tradingassistant.platform.api.dto.WatchlistDtos;
import com.tradingassistant.platform.security.AuthenticatedUser;
import com.tradingassistant.platform.watchlist.WatchlistService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Account-private watchlists. The owner identity always comes from the access
 * token, never from the path or the body.
 */
@RestController
@RequestMapping(path = "/api/v1/watchlists", produces = MediaType.APPLICATION_JSON_VALUE)
public class WatchlistController {

    private final WatchlistService watchlists;

    public WatchlistController(WatchlistService watchlists) {
        this.watchlists = watchlists;
    }

    @GetMapping
    public ApiResponse<List<WatchlistDtos.WatchlistView>> list(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(watchlists.list(user.userId()).stream()
                .map(WatchlistDtos.WatchlistView::from).toList());
    }

    @GetMapping("/{id}")
    public ApiResponse<WatchlistDtos.WatchlistView> get(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id) {
        return ApiResponse.success(WatchlistDtos.WatchlistView.from(
                watchlists.require(user.userId(), id)));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<WatchlistDtos.WatchlistView>> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody WatchlistDtos.CreateWatchlistRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                WatchlistDtos.WatchlistView.from(
                        watchlists.create(user.userId(), request.name()))));
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<WatchlistDtos.WatchlistView> rename(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id,
            @Valid @RequestBody WatchlistDtos.RenameWatchlistRequest request) {
        return ApiResponse.success(WatchlistDtos.WatchlistView.from(
                watchlists.rename(user.userId(), id, request.name())));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<WatchlistDtos.WatchlistView> delete(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id) {
        WatchlistDtos.WatchlistView view = WatchlistDtos.WatchlistView.from(
                watchlists.require(user.userId(), id));
        watchlists.delete(user.userId(), id);
        return ApiResponse.success(view);
    }

    @PostMapping(path = "/{id}/items", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<WatchlistDtos.WatchlistView> addItem(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id,
            @Valid @RequestBody WatchlistDtos.AddItemRequest request) {
        return ApiResponse.success(WatchlistDtos.WatchlistView.from(
                watchlists.addItem(user.userId(), id, request.symbolToken(),
                        request.symbol(), request.segment())));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public ApiResponse<WatchlistDtos.WatchlistView> removeItem(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id,
            @PathVariable String itemId) {
        return ApiResponse.success(WatchlistDtos.WatchlistView.from(
                watchlists.removeItem(user.userId(), id, itemId)));
    }
}
