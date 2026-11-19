package com.tradingassistant.platform.user;

import com.tradingassistant.platform.security.Role;
import com.tradingassistant.platform.security.SecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Creates the first administrator on a fresh deployment.
 *
 * <p>Both values come from the environment ({@code APP_ADMIN_EMAIL} and
 * {@code APP_ADMIN_PASSWORD}). Nothing is created when they are absent, and an
 * existing account is never escalated or overwritten.</p>
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);
    private static final String DEFAULT_DISPLAY_NAME = "Administrator";

    private final SecurityProperties properties;
    private final UserService userService;

    public AdminBootstrapRunner(SecurityProperties properties, UserService userService) {
        this.properties = properties;
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.bootstrapAdminConfigured()) {
            return;
        }
        try {
            if (userService.findByEmail(properties.bootstrapAdminEmail()).isPresent()) {
                log.info("Administrator account already exists; bootstrap skipped");
                return;
            }
            userService.create(properties.bootstrapAdminEmail(), DEFAULT_DISPLAY_NAME,
                    properties.bootstrapAdminPassword(), Set.of(Role.ADMIN, Role.USER));
            log.info("Bootstrapped the first administrator account");
        } catch (RuntimeException failure) {
            log.warn("Administrator bootstrap skipped: {}", failure.getMessage());
        }
    }
}
