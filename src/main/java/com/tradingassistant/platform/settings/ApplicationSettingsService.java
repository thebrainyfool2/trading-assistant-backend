package com.tradingassistant.platform.settings;

import com.tradingassistant.common.api.ErrorCode;
import com.tradingassistant.platform.error.PlatformException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Reads and updates the settings singleton.
 *
 * <p>Updates are restricted to administrators by the controller; the service
 * records who changed the settings and when.</p>
 */
@Service
public class ApplicationSettingsService {

    private final ApplicationSettingsRepository repository;
    private final Clock clock;

    public ApplicationSettingsService(ApplicationSettingsRepository repository,
                                      Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public ApplicationSettingsDocument get() {
        return repository.findById(ApplicationSettingsDocument.SINGLETON_ID)
                .orElseGet(() -> {
                    try {
                        return repository.save(ApplicationSettingsDocument.defaults(
                                "system", clock.instant()));
                    } catch (DuplicateKeyException race) {
                        return repository.findById(
                                        ApplicationSettingsDocument.SINGLETON_ID)
                                .orElseThrow(() -> new PlatformException(
                                        ErrorCode.INTERNAL_ERROR,
                                        "Application settings could not be created."));
                    }
                });
    }

    public ApplicationSettingsDocument update(String adminId, boolean maintenanceMode,
                                              int maximumWatchlistItems,
                                              int historyRetentionDays) {
        ApplicationSettingsDocument current = get();
        try {
            return repository.save(current.update(maintenanceMode,
                    maximumWatchlistItems, historyRetentionDays, adminId,
                    clock.instant()));
        } catch (IllegalArgumentException failure) {
            throw new PlatformException(ErrorCode.VALIDATION_FAILED,
                    failure.getMessage());
        }
    }
}
