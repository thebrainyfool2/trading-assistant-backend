package com.tradingassistant.broker.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Token-free view of the broker session. It is safe to expose to operational
 * code but should not be used as an authentication credential.
 */
public record SessionStatus(
        BrokerName broker,
        boolean authenticated,
        Instant issuedAt,
        Instant expiresAt,
        boolean renewalSupported
) {

    public SessionStatus {
        Objects.requireNonNull(broker, "broker must not be null");
    }

    public static SessionStatus unauthenticated(BrokerName broker) {
        return new SessionStatus(broker, false, null, null, false);
    }
}
