package com.tradingassistant.platform.preferences;

import com.tradingassistant.common.api.ErrorCode;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.platform.PlatformTestFixtures;
import com.tradingassistant.platform.error.PlatformException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserPreferencesServiceTest {

    private final UserPreferencesRepository repository =
            mock(UserPreferencesRepository.class);
    private final UserPreferencesService service = new UserPreferencesService(repository,
            Clock.fixed(PlatformTestFixtures.NOW, java.time.ZoneOffset.UTC));

    @Test
    void firstAccessCreatesDocumentedDefaults() {
        when(repository.findById("user-1")).thenReturn(Optional.empty());
        when(repository.save(any(UserPreferencesDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserPreferencesDocument preferences = service.getOrCreate("user-1");

        assertThat(preferences.theme()).isEqualTo(Theme.DARK);
        assertThat(preferences.defaultInterval()).isEqualTo(Interval.FIFTEEN_MINUTE);
        assertThat(preferences.notificationsEnabled()).isTrue();
        assertThat(preferences.orderNotifications()).isTrue();
        assertThat(preferences.updatedAt()).isEqualTo(PlatformTestFixtures.NOW);
    }

    @Test
    void existingPreferencesAreReturnedWithoutWriting() {
        UserPreferencesDocument stored = UserPreferencesDocument.defaults("user-1",
                PlatformTestFixtures.NOW);
        when(repository.findById("user-1")).thenReturn(Optional.of(stored));

        assertThat(service.getOrCreate("user-1")).isSameAs(stored);
        verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void updatePersistsEveryPreference() {
        when(repository.findById("user-1"))
                .thenReturn(Optional.of(UserPreferencesDocument.defaults("user-1",
                        PlatformTestFixtures.NOW)));
        when(repository.save(any(UserPreferencesDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserPreferencesDocument updated = service.update("user-1", Theme.DARK,
                Interval.FIVE_MINUTE, "EMA_TREND", true, false, true, true, false);

        assertThat(updated.defaultInterval()).isEqualTo(Interval.FIVE_MINUTE);
        assertThat(updated.defaultStrategyId()).isEqualTo("EMA_TREND");
        assertThat(updated.orderNotifications()).isFalse();
        assertThat(updated.systemMessages()).isFalse();
    }

    @Test
    void onlyDarkThemeIsAccepted() {
        assertThatThrownBy(() -> service.update("user-1", null, Interval.FIVE_MINUTE,
                "", true, true, true, true, true))
                .isInstanceOf(PlatformException.class)
                .extracting(failure -> ((PlatformException) failure).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void strategyIdentifierIsBounded() {
        assertThatThrownBy(() -> service.update("user-1", Theme.DARK,
                Interval.FIVE_MINUTE, "x".repeat(65), true, true, true, true, true))
                .isInstanceOf(PlatformException.class)
                .extracting(failure -> ((PlatformException) failure).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void nullIntervalFallsBackToTheDocumentedDefault() {
        when(repository.findById("user-1"))
                .thenReturn(Optional.of(UserPreferencesDocument.defaults("user-1",
                        PlatformTestFixtures.NOW)));
        when(repository.save(any(UserPreferencesDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.update("user-1", Theme.DARK, null, "", true, true, true,
                true, true).defaultInterval()).isEqualTo(Interval.FIFTEEN_MINUTE);
    }
}
