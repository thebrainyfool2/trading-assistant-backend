package com.tradingassistant.broker.api;

import com.tradingassistant.broker.model.BrokerProfile;
import com.tradingassistant.broker.model.SessionStatus;

/**
 * Authentication and account metadata operations supported by a broker.
 */
public interface AuthenticationProvider {

    /**
     * Establishes a session if one is not already valid. Implementations must be
     * safe to call concurrently.
     */
    SessionStatus authenticate();

    /**
     * Retrieves the authenticated account profile. Tokens are never returned.
     */
    BrokerProfile profile();

    /**
     * Destroys the broker session when supported and always clears the local
     * session state.
     */
    void logout();
}
