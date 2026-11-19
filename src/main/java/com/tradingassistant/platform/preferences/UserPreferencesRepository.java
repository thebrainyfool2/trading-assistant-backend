package com.tradingassistant.platform.preferences;

import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data repository for preferences.
 */
public interface UserPreferencesRepository
        extends MongoRepository<UserPreferencesDocument, String> {
}
