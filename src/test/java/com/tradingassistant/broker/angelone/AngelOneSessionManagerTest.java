package com.tradingassistant.broker.angelone;

import com.tradingassistant.broker.BrokerTestFixtures;
import com.tradingassistant.broker.angelone.auth.AngelOneAuthenticator;
import com.tradingassistant.broker.config.AngelOneProperties;
import com.tradingassistant.broker.exception.BrokerSessionExpiredException;
import com.tradingassistant.broker.exception.BrokerUnavailableException;
import com.tradingassistant.broker.model.SessionStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AngelOneSessionManagerTest {

    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");

    private final AngelOneAuthenticator authenticator = mock(AngelOneAuthenticator.class);
    private final AngelOneProperties properties = BrokerTestFixtures.enabledProperties();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void statusDoesNotAuthenticate() {
        AngelOneSessionManager manager = manager();

        SessionStatus status = manager.status();

        assertThat(status.authenticated()).isFalse();
        assertThat(status.broker().name()).isEqualTo("ANGEL_ONE");
        verify(authenticator, never()).login();
        verify(authenticator, never()).refresh(any());
    }

    @Test
    void authenticateCreatesOneSession() {
        AngelOneSession session = session(NOW.plus(Duration.ofHours(1)), "refresh", "feed");
        when(authenticator.login()).thenReturn(session);
        AngelOneSessionManager manager = manager();

        SessionStatus status = manager.authenticate();

        assertThat(status.authenticated()).isTrue();
        assertThat(status.expiresAt()).isEqualTo(session.expiresAt());
        assertThat(status.renewalSupported()).isTrue();
        verify(authenticator, times(1)).login();
    }

    @Test
    void concurrentAuthenticationCreatesOnlyOneSession() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        when(authenticator.login()).thenAnswer(invocation -> {
            entered.countDown();
            Thread.sleep(100);
            return session(NOW.plus(Duration.ofHours(1)), "refresh", "feed");
        });
        AngelOneSessionManager manager = manager();
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<SessionStatus>> futures = new ArrayList<>();
            for (int index = 0; index < 4; index++) {
                futures.add(executor.submit(manager::authenticate));
            }
            for (Future<SessionStatus> future : futures) {
                assertThat(future.get().authenticated()).isTrue();
            }
        } finally {
            executor.shutdownNow();
        }
        verify(authenticator, times(1)).login();
    }

    @Test
    void renewsAnExpiringSessionBeforeCallingLogin() {
        AngelOneSession expiring = session(NOW.plus(Duration.ofSeconds(30)), "refresh", "feed");
        AngelOneSession renewed = session(NOW.plus(Duration.ofHours(2)), "refresh-2", "feed-2");
        when(authenticator.login()).thenReturn(expiring);
        when(authenticator.refresh(expiring)).thenReturn(renewed);
        AngelOneSessionManager manager = manager();
        manager.authenticate();

        SessionStatus status = manager.ensureAuthenticated();

        assertThat(status.expiresAt()).isEqualTo(renewed.expiresAt());
        verify(authenticator, times(1)).refresh(expiring);
        verify(authenticator, times(1)).login();
    }

    @Test
    void fallsBackToLoginWhenRefreshIsRejected() {
        AngelOneSession expiring = session(NOW.plus(Duration.ofSeconds(30)), "refresh", "feed");
        AngelOneSession reauthenticated = session(NOW.plus(Duration.ofHours(2)), "refresh-2", "feed-2");
        when(authenticator.login()).thenReturn(expiring, reauthenticated);
        when(authenticator.refresh(expiring)).thenThrow(new BrokerSessionExpiredException("expired"));
        AngelOneSessionManager manager = manager();
        manager.authenticate();

        SessionStatus status = manager.ensureAuthenticated();

        assertThat(status.authenticated()).isTrue();
        assertThat(status.expiresAt()).isEqualTo(reauthenticated.expiresAt());
        verify(authenticator, times(2)).login();
        verify(authenticator, times(1)).refresh(expiring);
    }

    @Test
    void doesNotFallBackToLoginOnTransientNetworkFailure() {
        AngelOneSession expiring = session(NOW.plus(Duration.ofSeconds(30)), "refresh", "feed");
        when(authenticator.login()).thenReturn(expiring);
        when(authenticator.refresh(expiring)).thenThrow(new BrokerUnavailableException("down"));
        AngelOneSessionManager manager = manager();
        manager.authenticate();

        assertThatThrownBy(manager::ensureAuthenticated)
                .isInstanceOf(BrokerUnavailableException.class);
        verify(authenticator, times(1)).login();
        verify(authenticator, times(1)).refresh(expiring);
    }

    @Test
    void forceRenewRefreshesAValidButRejectedSession() {
        AngelOneSession current = session(NOW.plus(Duration.ofHours(1)), "refresh", "feed");
        AngelOneSession renewed = session(NOW.plus(Duration.ofHours(2)), "refresh-2", "feed-2");
        when(authenticator.login()).thenReturn(current);
        when(authenticator.refresh(current)).thenReturn(renewed);
        AngelOneSessionManager manager = manager();
        manager.authenticate();

        manager.forceRenew();

        assertThat(manager.status().expiresAt()).isEqualTo(renewed.expiresAt());
        verify(authenticator, times(1)).refresh(current);
    }

    @Test
    void logoutClearsLocalSessionEvenWhenBrokerLogoutFails() {
        AngelOneSession session = session(NOW.plus(Duration.ofHours(1)), "refresh", "feed");
        when(authenticator.login()).thenReturn(session);
        doThrow(new BrokerUnavailableException("down")).when(authenticator).logout(session);
        AngelOneSessionManager manager = manager();
        manager.authenticate();

        manager.logout();

        assertThat(manager.status().authenticated()).isFalse();
        verify(authenticator, times(1)).logout(session);
    }

    @Test
    void feedTokenFailsWhenBrokerDidNotIssueOne() {
        AngelOneSession session = session(NOW.plus(Duration.ofHours(1)), "refresh", null);
        when(authenticator.login()).thenReturn(session);
        AngelOneSessionManager manager = manager();

        assertThatThrownBy(manager::feedToken)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("feed token");
    }

    private AngelOneSessionManager manager() {
        return new AngelOneSessionManager(authenticator, properties, clock);
    }

    private static AngelOneSession session(Instant expiresAt, String refreshToken, String feedToken) {
        return new AngelOneSession(
                "test-client",
                "test-jwt",
                refreshToken,
                feedToken,
                NOW,
                expiresAt);
    }
}
