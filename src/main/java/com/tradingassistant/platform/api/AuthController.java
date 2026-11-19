package com.tradingassistant.platform.api;

import com.tradingassistant.common.api.ApiResponse;
import com.tradingassistant.platform.api.dto.AuthDtos;
import com.tradingassistant.platform.security.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints. Every method delegates to
 * {@link AuthService}; no credential logic lives in the controller.
 */
@RestController
@RequestMapping(path = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping(path = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<AuthDtos.AuthResponse>> register(
            @Valid @RequestBody AuthDtos.RegisterRequest request) {
        AuthService.AuthResult result = authService.register(request.email(),
                request.displayName(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(AuthDtos.AuthResponse.from(result)));
    }

    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AuthDtos.AuthResponse> login(
            @Valid @RequestBody AuthDtos.LoginRequest request) {
        return ApiResponse.success(AuthDtos.AuthResponse.from(
                authService.login(request.email(), request.password())));
    }

    @PostMapping(path = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AuthDtos.AuthResponse> refresh(
            @Valid @RequestBody AuthDtos.RefreshRequest request) {
        return ApiResponse.success(AuthDtos.AuthResponse.from(
                authService.refresh(request.refreshToken())));
    }

    @PostMapping(path = "/logout", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AuthDtos.LogoutResponse> logout(
            @Valid @RequestBody AuthDtos.LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.success(AuthDtos.LogoutResponse.done());
    }
}
