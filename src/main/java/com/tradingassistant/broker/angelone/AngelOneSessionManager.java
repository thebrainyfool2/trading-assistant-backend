package com.tradingassistant.broker.angelone;

import com.tradingassistant.broker.angelone.auth.AngelOneAuthenticator;
import com.tradingassistant.broker.config.AngelOneProperties;
import com.tradingassistant.broker.exception.BrokerAuthenticationException;
import com.tradingassistant.broker.exception.BrokerError;
import com.tradingassistant.broker.exception.BrokerException;
import com.tradingassistant.broker.model.BrokerName;
import com.tradingassistant.broker.model.SessionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;

/**
 * Owns the current SmartAPI session. Expiry, renewal and re-authentication are
 * serialized so that concurrent callers cannot create duplicate sessions. The
 * manager exposes token-free status objects to the rest of the application.
 */
public class AngelOneSessionManager {

    private static final Logger log = LoggerFactory.getLogger(AngelOneSessionManager.class);
    private static final BrokerName BROKER = BrokerName.ANGEL_ONE;

    private final AngelOneAuthenticator authenticator;
    private final AngelOneProperties properties;
    private final Clock clock;
    private final Object lock = new Object();

    private volatile AngelOneSession session;

    public AngelOneSessionManager(AngelOneAuthenticator authenticator,
                                  AngelOneProperties properties,
                                  Clock clock) {
        this.authenticator = authenticator;
        this.properties = properties;
        this.clock = clock;
    }

    public SessionStatus status() {
        AngelOneSession current = session;
        if (current == null || current.isExpired(clock.instant())) {
            return SessionStatus.unauthenticated(BROKER);
        }
        return statusOf(current);
    }

    public SessionStatus authenticate() {
        synchronized (lock) {
            AngelOneSession current = session;
            Instant now = clock.instant();
            if (current != null && !current.isExpiring(now, properties.sessionExpirySkew())) {
                return statusOf(current);
            }
            return statusOf(renewOrLogin(current, now));
        }
    }

    public SessionStatus ensureAuthenticated() {
        return authenticate();
    }

    public boolean isAuthenticated() {
        return status().authenticated();
    }

    /**
     * Returns a usable session, renewing it when it is close to expiry. This is
     * the only token-bearing access point and remains internal to the adapter.
     */
    AngelOneSession currentSession() {
        AngelOneSession current = session;
        Instant now = clock.instant();
        if (current != null && !current.isExpiring(now, properties.sessionExpirySkew())) {
            return current;
        }

        synchronized (lock) {
            current = session;
            now = clock.instant();
            if (current != null && !current.isExpiring(now, properties.sessionExpirySkew())) {
                return current;
            }
            return renewOrLogin(current, now);
        }
    }

    /**
     * Forces one renewal after a broker rejects a token that was still inside
     * the local expiry window. This handles clock skew and server-side session
     * revocation without creating a duplicate login on every request.
     */
    public void forceRenew() {
        synchronized (lock) {
            renewOrLogin(session, clock.instant());
        }
    }

    public String accessToken() {
        return currentSession().accessToken();
    }

    public String feedToken() {
        AngelOneSession current = currentSession();
        if (current.feedToken() == null || current.feedToken().isBlank()) {
            throw new BrokerAuthenticationException(
                    BrokerError.AUTHENTICATION_FAILED, "The broker did not issue a feed token.");
        }
        return current.feedToken();
    }

    public void logout() {
        AngelOneSession current;
        synchronized (lock) {
            current = session;
            session = null;
        }
        if (current == null) {
            return;
        }
        try {
            authenticator.logout(current);
            log.info("broker_session_logged_out");
        } catch (BrokerException exception) {
            // Local state is already cleared. A remote logout failure must not
            // leave a token usable for later requests.
            log.warn("broker_session_logout_failed error={} brokerCode={}",
                    exception.error(), exception.brokerCode());
        }
    }

    private AngelOneSession renewOrLogin(AngelOneSession current, Instant now) {
        if (current != null && current.canRefresh()) {
            try {
                AngelOneSession refreshed = authenticator.refresh(current);
                session = refreshed;
                log.info("broker_session_renewed expiresAt={}", refreshed.expiresAt());
                return refreshed;
            } catch (BrokerException exception) {
                if (!canFallbackToLogin(exception)) {
                    throw exception;
                }
                log.warn("broker_session_renewal_failed error={} brokerCode={} action=relogin",
                        exception.error(), exception.brokerCode());
            }
        }

        try {
            AngelOneSession created = authenticator.login();
            session = created;
            log.info("broker_session_authenticated expiresAt={}", created.expiresAt());
            return created;
        } catch (BrokerException exception) {
            session = null;
            throw exception;
        }
    }

    private static boolean canFallbackToLogin(BrokerException exception) {
        return switch (exception.error()) {
            case SESSION_EXPIRED, AUTHENTICATION_FAILED, INVALID_TOTP, REQUEST_REJECTED -> true;
            case CONFIGURATION, RATE_LIMITED, UNAVAILABLE, NETWORK, INVALID_RESPONSE, WEBSOCKET -> false;
        };
    }

    private static SessionStatus statusOf(AngelOneSession current) {
        return new SessionStatus(
                BROKER,
                true,
                current.issuedAt(),
                current.expiresAt(),
                current.canRefresh());
    }

    @Override
    public String toString() {
        return "AngelOneSessionManager[broker=" + BROKER + ", authenticated=" + isAuthenticated() + "]";
    }
}
