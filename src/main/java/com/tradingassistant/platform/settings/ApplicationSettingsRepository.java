package com.tradingassistant.platform.settings;

import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data repository for the settings singleton.
 */
public interface ApplicationSettingsRepository
        extends MongoRepository<ApplicationSettingsDocument, String> {
}
