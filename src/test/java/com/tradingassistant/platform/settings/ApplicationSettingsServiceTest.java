package com.tradingassistant.platform.settings;

import com.tradingassistant.common.api.ErrorCode;
import com.tradingassistant.platform.PlatformTestFixtures;
import com.tradingassistant.platform.error.PlatformException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApplicationSettingsServiceTest {

    private final ApplicationSettingsRepository repository =
            mock(ApplicationSettingsRepository.class);
    private final ApplicationSettingsService service = new ApplicationSettingsService(
            repository, Clock.fixed(PlatformTestFixtures.NOW,
            java.time.ZoneOffset.UTC));

    @Test
    void firstAccessCreatesDefaults() {
        when(repository.findById("application")).thenReturn(Optional.empty());
        when(repository.save(any(ApplicationSettingsDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ApplicationSettingsDocument settings = service.get();

        assertThat(settings.maintenanceMode()).isFalse();
        assertThat(settings.maximumWatchlistItems()).isEqualTo(100);
        assertThat(settings.historyRetentionDays()).isEqualTo(365);
        assertThat(settings.updatedBy()).isEqualTo("system");
    }

    @Test
    void updateRecordsTheAdministrator() {
        when(repository.findById("application")).thenReturn(Optional.of(
                ApplicationSettingsDocument.defaults("system",
                        PlatformTestFixtures.NOW)));
        when(repository.save(any(ApplicationSettingsDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ApplicationSettingsDocument updated = service.update("admin-1", true, 50, 90);

        assertThat(updated.maintenanceMode()).isTrue();
        assertThat(updated.maximumWatchlistItems()).isEqualTo(50);
        assertThat(updated.historyRetentionDays()).isEqualTo(90);
        assertThat(updated.updatedBy()).isEqualTo("admin-1");
    }

    @Test
    void outOfRangeSettingsAreRejected() {
        when(repository.findById("application")).thenReturn(Optional.of(
                ApplicationSettingsDocument.defaults("system",
                        PlatformTestFixtures.NOW)));

        assertThatThrownBy(() -> service.update("admin-1", false, 0, 90))
                .isInstanceOf(PlatformException.class)
                .extracting(failure -> ((PlatformException) failure).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThatThrownBy(() -> service.update("admin-1", false, 50, 10))
                .isInstanceOf(PlatformException.class);
    }

    @Test
    void existingSettingsAreReturnedWithoutWriting() {
        ApplicationSettingsDocument stored = ApplicationSettingsDocument.defaults(
                "system", PlatformTestFixtures.NOW);
        when(repository.findById("application")).thenReturn(Optional.of(stored));

        assertThat(service.get()).isSameAs(stored);
    }
}
