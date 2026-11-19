package com.tradingassistant.platform.api;

import com.tradingassistant.common.api.ApiResponse;
import com.tradingassistant.platform.api.dto.NotificationDtos;
import com.tradingassistant.platform.api.dto.PageResponse;
import com.tradingassistant.platform.notification.NotificationService;
import com.tradingassistant.platform.security.AuthenticatedUser;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * In-app notifications for the authenticated account.
 */
@RestController
@RequestMapping(path = "/api/v1/notifications",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public ApiResponse<PageResponse<NotificationDtos.NotificationView>> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(PageResponse.of(
                notifications.list(user.userId(), page, size),
                NotificationDtos.NotificationView::from));
    }

    @GetMapping("/unread")
    public ApiResponse<NotificationDtos.UnreadCountView> unread(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(new NotificationDtos.UnreadCountView(
                notifications.unreadCount(user.userId())));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<NotificationDtos.NotificationView> markRead(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id) {
        return ApiResponse.success(NotificationDtos.NotificationView.from(
                notifications.markRead(user.userId(), id)));
    }

    @PutMapping("/read-all")
    public ApiResponse<NotificationDtos.UnreadCountView> markAllRead(
            @AuthenticationPrincipal AuthenticatedUser user) {
        notifications.markAllRead(user.userId());
        return ApiResponse.success(new NotificationDtos.UnreadCountView(
                notifications.unreadCount(user.userId())));
    }
}
