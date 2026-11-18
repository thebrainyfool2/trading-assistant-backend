package com.tradingassistant.broker.api;

import com.tradingassistant.broker.model.SessionStatus;

/**
 * Read and lifecycle operations for the broker session. Implementations are
 * responsible for expiry tracking and renewal.
 */
public interface SessionProvider {

    /**
     * Returns the current status without forcing a login or revealing tokens.
     */
    SessionStatus sessionStatus();

    /**
     * Ensures a usable session exists, renewing or authenticating as needed.
     */
    SessionStatus ensureAuthenticated();

    boolean isAuthenticated();
}
