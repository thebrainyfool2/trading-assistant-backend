package com.tradingassistant.platform.preferences;

import com.tradingassistant.common.api.ErrorCode;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.platform.error.PlatformException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Reads and updates account preferences, creating documented defaults on first
 * access.
 */
@Service
public class UserPreferencesService {

    private static final int MAXIMUM_STRATEGY_ID_LENGTH = 64;

    private final UserPreferencesRepository repository;
    private final Clock clock;

    public UserPreferencesService(UserPreferencesRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public UserPreferencesDocument getOrCreate(String userId) {
        return repository.findById(userId).orElseGet(() -> {
            try {
                return repository.save(UserPreferencesDocument.defaults(userId,
                        clock.instant()));
            } catch (DuplicateKeyException race) {
                return repository.findById(userId)
                        .orElseThrow(() -> new PlatformException(
                                ErrorCode.INTERNAL_ERROR,
                                "Preferences could not be created."));
            }
        });
    }

    public UserPreferencesDocument update(String userId, Theme theme,
                                          Interval defaultInterval,
                                          String defaultStrategyId,
                                          boolean notificationsEnabled,
                                          boolean orderNotifications,
                                          boolean positionNotifications,
                                          boolean loginAlerts,
                                          boolean systemMessages) {
        if (theme != Theme.DARK) {
            throw new PlatformException(ErrorCode.VALIDATION_FAILED,
                    "Only the DARK theme is supported.");
        }
        Interval interval = defaultInterval == null
                ? Interval.FIFTEEN_MINUTE : defaultInterval;
        String strategy = defaultStrategyId == null ? "" : defaultStrategyId.trim();
        if (strategy.length() > MAXIMUM_STRATEGY_ID_LENGTH) {
            throw new PlatformException(ErrorCode.VALIDATION_FAILED,
                    "defaultStrategyId must not exceed " + MAXIMUM_STRATEGY_ID_LENGTH
                            + " characters.");
        }
        UserPreferencesDocument current = getOrCreate(userId);
        return repository.save(current.update(theme, interval, strategy,
                notificationsEnabled, orderNotifications, positionNotifications,
                loginAlerts, systemMessages, clock.instant()));
    }
}
