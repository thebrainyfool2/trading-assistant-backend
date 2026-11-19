package com.tradingassistant.platform.user;

import com.tradingassistant.common.api.ErrorCode;
import com.tradingassistant.platform.PlatformTestFixtures;
import com.tradingassistant.platform.error.PlatformException;
import com.tradingassistant.platform.security.Role;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private final UserRepository repository = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final UserService service = new UserService(repository, encoder,
            Clock.fixed(PlatformTestFixtures.NOW, java.time.ZoneOffset.UTC));

    @Test
    void registerNormalisesEmailAndUsesUserRole() {
        when(encoder.encode("password123")).thenReturn("hashed");
        when(repository.existsByEmail("user@example.com")).thenReturn(false);
        when(repository.save(any(UserDocument.class))).thenAnswer(invocation -> {
            UserDocument document = invocation.getArgument(0);
            return new UserDocument("id-1", document.email(), document.displayName(),
                    document.passwordHash(), document.roles(), document.status(),
                    document.createdAt(), document.updatedAt(), document.lastLoginAt());
        });

        UserDocument saved = service.register("  User@Example.COM ",
                "  Test User ", "password123");

        assertThat(saved.email()).isEqualTo("user@example.com");
        assertThat(saved.displayName()).isEqualTo("Test User");
        assertThat(saved.passwordHash()).isEqualTo("hashed");
        assertThat(saved.roles()).containsExactly(Role.USER);
        assertThat(saved.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void registerRejectsUnsafePassword() {
        assertThatThrownBy(() -> service.register("user@example.com", "User", "short"))
                .isInstanceOf(PlatformException.class)
                .extracting(failure -> ((PlatformException) failure).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        verify(repository, never()).save(any());
    }

    @Test
    void registerRejectsExistingEmail() {
        when(repository.existsByEmail("user@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register("user@example.com", "User",
                "password123"))
                .isInstanceOf(PlatformException.class)
                .extracting(failure -> ((PlatformException) failure).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void authenticateUpdatesLastLogin() {
        UserDocument stored = PlatformTestFixtures.user();
        when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(stored));
        when(encoder.matches("password123", "bcrypt-hash")).thenReturn(true);
        when(repository.save(any(UserDocument.class))).thenAnswer(invocation ->
                invocation.getArgument(0));

        UserDocument authenticated = service.authenticate("USER@example.com",
                "password123");

        assertThat(authenticated.lastLoginAt()).isEqualTo(PlatformTestFixtures.NOW);
    }

    @Test
    void authenticateRejectsUnknownEmailWithoutRevealingIt() {
        when(repository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticate("missing@example.com",
                "password123"))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("Email or password is incorrect");
    }

    @Test
    void authenticateRejectsWrongPassword() {
        when(repository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(PlatformTestFixtures.user()));
        when(encoder.matches("wrong", "bcrypt-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.authenticate("user@example.com", "wrong"))
                .isInstanceOf(PlatformException.class)
                .extracting(failure -> ((PlatformException) failure).errorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void disabledAccountCannotAuthenticate() {
        UserDocument disabled = new UserDocument("id-1", "user@example.com", "User",
                "bcrypt-hash", Set.of(Role.USER), UserStatus.DISABLED,
                PlatformTestFixtures.NOW, PlatformTestFixtures.NOW, null);
        when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(disabled));
        when(encoder.matches("password123", "bcrypt-hash")).thenReturn(true);

        assertThatThrownBy(() -> service.authenticate("user@example.com",
                "password123"))
                .isInstanceOf(PlatformException.class)
                .extracting(failure -> ((PlatformException) failure).errorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void displayNameIsTrimmedAndBounded() {
        when(repository.findById("id-1"))
                .thenReturn(Optional.of(PlatformTestFixtures.user()));
        when(repository.save(any(UserDocument.class))).thenAnswer(invocation ->
                invocation.getArgument(0));

        UserDocument updated = service.updateDisplayName("id-1", "  New Name  ");
        assertThat(updated.displayName()).isEqualTo("New Name");

        assertThatThrownBy(() -> service.updateDisplayName("id-1", "x".repeat(81)))
                .isInstanceOf(PlatformException.class)
                .extracting(failure -> ((PlatformException) failure).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void passwordChangeVerifiesCurrentPasswordAndHashesNewOne() {
        when(repository.findById("id-1"))
                .thenReturn(Optional.of(PlatformTestFixtures.user()));
        when(encoder.matches("current-password", "bcrypt-hash")).thenReturn(true);
        when(encoder.encode("new-password-123")).thenReturn("new-hash");
        when(repository.save(any(UserDocument.class))).thenAnswer(invocation ->
                invocation.getArgument(0));

        service.changePassword("id-1", "current-password", "new-password-123");

        ArgumentCaptor<UserDocument> saved = ArgumentCaptor.forClass(UserDocument.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().passwordHash()).isEqualTo("new-hash");
    }

    @Test
    void passwordChangeRejectsWrongCurrentPassword() {
        when(repository.findById("id-1"))
                .thenReturn(Optional.of(PlatformTestFixtures.user()));
        when(encoder.matches("wrong", "bcrypt-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword("id-1", "wrong",
                "new-password-123"))
                .isInstanceOf(PlatformException.class)
                .extracting(failure -> ((PlatformException) failure).errorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(repository, never()).save(any());
    }

    @Test
    void lastActiveAdministratorCannotLoseTheRole() {
        UserDocument admin = PlatformTestFixtures.user("admin-1",
                "admin@example.com", Role.ADMIN);
        when(repository.findById("admin-1")).thenReturn(Optional.of(admin));
        when(repository.countByRolesContainingAndStatus(Role.ADMIN, UserStatus.ACTIVE))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.updateRoles("admin-1", Set.of(Role.USER)))
                .isInstanceOf(PlatformException.class)
                .extracting(failure -> ((PlatformException) failure).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void lastActiveAdministratorCannotBeDisabled() {
        UserDocument admin = PlatformTestFixtures.user("admin-1",
                "admin@example.com", Role.ADMIN);
        when(repository.findById("admin-1")).thenReturn(Optional.of(admin));
        when(repository.countByRolesContainingAndStatus(Role.ADMIN, UserStatus.ACTIVE))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.updateStatus("admin-1", UserStatus.DISABLED))
                .isInstanceOf(PlatformException.class)
                .extracting(failure -> ((PlatformException) failure).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void usersAreListedNewestFirstThroughTheRepositoryPage() {
        Page<UserDocument> page = Page.empty();
        when(repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 20)))
                .thenReturn(page);

        assertThat(service.list(PageRequest.of(0, 20))).isSameAs(page);
    }

    @Test
    void emailNormalisationRejectsBlankValues() {
        assertThatThrownBy(() -> UserService.normalizeEmail(" "))
                .isInstanceOf(PlatformException.class);
        assertThat(UserService.normalizeEmail(" User@Example.com "))
                .isEqualTo("user@example.com");
    }
}
