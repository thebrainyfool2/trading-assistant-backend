package com.tradingassistant.system.domain;

/**
 * Application metadata exposed by the system information use case. This model is
 * independent of the HTTP representation.
 */
public record SystemInfo(String name, String version, String environment, String apiVersion) {
}
