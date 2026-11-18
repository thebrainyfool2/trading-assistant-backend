package com.tradingassistant.system.api.dto;

import com.tradingassistant.system.domain.SystemInfo;

/**
 * HTTP representation of application metadata. The API DTO is deliberately
 * separate from the domain model so the two can evolve independently.
 */
public record SystemInfoResponse(String name, String version, String environment, String apiVersion) {

    public static SystemInfoResponse from(SystemInfo systemInfo) {
        return new SystemInfoResponse(
                systemInfo.name(),
                systemInfo.version(),
                systemInfo.environment(),
                systemInfo.apiVersion()
        );
    }
}
