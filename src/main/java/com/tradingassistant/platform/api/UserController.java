package com.tradingassistant.platform.api;

import com.tradingassistant.common.api.ApiResponse;
import com.tradingassistant.common.api.ErrorCode;
import com.tradingassistant.platform.api.dto.PageResponse;
import com.tradingassistant.platform.api.dto.UserDtos;
import com.tradingassistant.platform.error.PlatformException;
import com.tradingassistant.platform.security.AuthService;
import com.tradingassistant.platform.security.AuthenticatedUser;
import com.tradingassistant.platform.security.RefreshTokenService;
import com.tradingassistant.platform.security.Role;
import com.tradingassistant.platform.user.UserDocument;
import com.tradingassistant.platform.user.UserService;
import com.tradingassistant.platform.user.UserStatus;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account profile, session and administrative user endpoints.
 */
@RestController
@RequestMapping(path = "/api/v1/users", produces = MediaType.APPLICATION_JSON_VALUE)
public class UserController {

    private static final int MAXIMUM_PAGE_SIZE = 100;

    private final UserService userService;
    private final RefreshTokenService refreshTokens;
    private final AuthService authService;

    public UserController(UserService userService, RefreshTokenService refreshTokens,
                          AuthService authService) {
        this.userService = userService;
        this.refreshTokens = refreshTokens;
        this.authService = authService;
    }

    @GetMapping("/me")
    public ApiResponse<UserDtos.UserView> me(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(UserDtos.UserView.from(userService.require(user.userId())));
    }

    @PutMapping(path = "/me", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<UserDtos.UserView> updateProfile(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UserDtos.UpdateProfileRequest request) {
        return ApiResponse.success(UserDtos.UserView.from(
                userService.updateDisplayName(user.userId(), request.displayName())));
    }

    @PutMapping(path = "/me/password", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<UserDtos.SessionCountView> changePassword(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UserDtos.ChangePasswordRequest request) {
        userService.changePassword(user.userId(), request.currentPassword(),
                request.newPassword());
        refreshTokens.revokeAll(user.userId());
        return ApiResponse.success(new UserDtos.SessionCountView(0));
    }

    @GetMapping("/me/sessions")
    public ApiResponse<UserDtos.SessionCountView> sessions(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(new UserDtos.SessionCountView(
                refreshTokens.activeCount(user.userId())));
    }

    @PutMapping("/me/logout-all")
    public ApiResponse<UserDtos.SessionCountView> logoutAll(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(new UserDtos.SessionCountView(
                authService.logoutAll(user.userId())));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<UserDtos.UserView>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(PageResponse.of(
                userService.list(PageRequest.of(Math.max(page, 0), clamp(size))),
                UserDtos.UserView::from));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserDtos.UserView> get(@PathVariable String id) {
        return ApiResponse.success(UserDtos.UserView.from(userService.require(id)));
    }

    @PutMapping(path = "/{id}/roles", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserDtos.UserView> updateRoles(
            @AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable String id,
            @Valid @RequestBody UserDtos.UpdateRolesRequest request) {
        if (admin.userId().equals(id) && !request.roles().contains(Role.ADMIN)) {
            throw new PlatformException(ErrorCode.CONFLICT,
                    "You cannot remove your own administrator role.");
        }
        return ApiResponse.success(UserDtos.UserView.from(
                userService.updateRoles(id, request.roles())));
    }

    @PutMapping(path = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserDtos.UserView> updateStatus(
            @AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable String id,
            @Valid @RequestBody UserDtos.UpdateStatusRequest request) {
        if (admin.userId().equals(id) && request.status() == UserStatus.DISABLED) {
            throw new PlatformException(ErrorCode.CONFLICT,
                    "You cannot disable your own account.");
        }
        return ApiResponse.success(UserDtos.UserView.from(
                userService.updateStatus(id, request.status())));
    }

    private static int clamp(int size) {
        return Math.min(Math.max(size, 1), MAXIMUM_PAGE_SIZE);
    }
}
