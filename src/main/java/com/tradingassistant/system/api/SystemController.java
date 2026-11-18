package com.tradingassistant.system.api;

import com.tradingassistant.common.api.ApiResponse;
import com.tradingassistant.system.api.dto.SystemInfoResponse;
import com.tradingassistant.system.application.SystemInfoService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operational endpoint used to verify which application build and environment is
 * serving traffic. It contains no business logic.
 */
@RestController
@RequestMapping(path = "/api/v1/system", produces = MediaType.APPLICATION_JSON_VALUE)
public class SystemController {

    private final SystemInfoService systemInfoService;

    public SystemController(SystemInfoService systemInfoService) {
        this.systemInfoService = systemInfoService;
    }

    @GetMapping("/info")
    public ApiResponse<SystemInfoResponse> getSystemInfo() {
        SystemInfoResponse response = SystemInfoResponse.from(systemInfoService.getSystemInfo());
        return ApiResponse.success(response);
    }
}
