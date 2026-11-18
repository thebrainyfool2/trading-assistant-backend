package com.tradingassistant.system.application;

import com.tradingassistant.config.ApplicationProperties;
import com.tradingassistant.system.domain.SystemInfo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Provides read-only application metadata for operational verification.
 */
@Service
public class SystemInfoService {

    private static final String UNKNOWN_VERSION = "unknown";

    private final ApplicationProperties applicationProperties;
    private final ObjectProvider<BuildProperties> buildPropertiesProvider;
    private final Environment environment;

    public SystemInfoService(ApplicationProperties applicationProperties,
                             ObjectProvider<BuildProperties> buildPropertiesProvider,
                             Environment environment) {
        this.applicationProperties = applicationProperties;
        this.buildPropertiesProvider = buildPropertiesProvider;
        this.environment = environment;
    }

    public SystemInfo getSystemInfo() {
        return new SystemInfo(
                applicationProperties.name(),
                resolveVersion(),
                resolveEnvironment(),
                applicationProperties.apiVersion()
        );
    }

    private String resolveVersion() {
        return Optional.ofNullable(buildPropertiesProvider.getIfAvailable())
                .map(BuildProperties::getVersion)
                .orElse(UNKNOWN_VERSION);
    }

    private String resolveEnvironment() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length > 0) {
            return String.join(",", activeProfiles);
        }

        String[] defaultProfiles = environment.getDefaultProfiles();
        return defaultProfiles.length == 0 ? "default" : String.join(",", defaultProfiles);
    }
}
