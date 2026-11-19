package com.tradingassistant.platform.user;

import com.tradingassistant.common.api.ErrorCode;
import com.tradingassistant.platform.error.PlatformException;
import com.tradingassistant.platform.security.Role;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Account use cases: registration, credential verification, profile changes and
 * administrative management.
 *
 * <p>The service is the only place that hashes or compares passwords. Callers
 * receive either a stored account or a platform error; no method reveals whether
 * an email exists except where the operation requires it.</p>
 */
@Service
public class UserService {

    private static final int MINIMUM_PASSWORD_LENGTH = 8;
    private static final int MAXIMUM_PASSWORD_LENGTH = 128;
    private static final int MAXIMUM_DISPLAY_NAME_LENGTH = 80;

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder,
                       Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * Registers a normal user account.
     */
    public UserDocument register(String email, String displayName, String rawPassword) {
        return create(email, displayName, rawPassword, Set.of(Role.USER));
    }

    /**
     * Creates an account with explicit roles. Used by registration and by the
     * optional administrator bootstrap.
     */
    public UserDocument create(String email, String displayName, String rawPassword,
                               Set<Role> roles) {
        String normalized = normalizeEmail(email);
        String name = normalizeDisplayName(displayName);
        validatePassword(rawPassword);
        if (repository.existsByEmail(normalized)) {
            throw new PlatformException(ErrorCode.CONFLICT,
                    "An account with this email already exists.");
        }
        Instant now = clock.instant();
        UserDocument document = UserDocument.create(normalized, name,
                passwordEncoder.encode(rawPassword), normaliseRoles(roles), now);
        try {
            return repository.save(document);
        } catch (DuplicateKeyException race) {
            throw new PlatformException(ErrorCode.CONFLICT,
                    "An account with this email already exists.");
        }
    }

    /**
     * Verifies credentials and records the successful authentication.
     */
    public UserDocument authenticate(String email, String rawPassword) {
        String normalized = normalizeEmail(email);
        Optional<UserDocument> candidate = repository.findByEmail(normalized);
        if (candidate.isEmpty()) {
            throw invalidCredentials();
        }
        UserDocument user = candidate.get();
        if (!passwordEncoder.matches(rawPassword == null ? "" : rawPassword,
                user.passwordHash())) {
            throw invalidCredentials();
        }
        if (!user.active()) {
            throw new PlatformException(ErrorCode.FORBIDDEN,
                    "This account is disabled.");
        }
        UserDocument updated = user.withLogin(clock.instant());
        return repository.save(updated);
    }

    public UserDocument require(String userId) {
        return repository.findById(userId)
                .orElseThrow(() -> new PlatformException(ErrorCode.RESOURCE_NOT_FOUND,
                        "User not found."));
    }

    public Optional<UserDocument> find(String userId) {
        return repository.findById(userId);
    }

    public Optional<UserDocument> findByEmail(String email) {
        return repository.findByEmail(normalizeEmail(email));
    }

    public Page<UserDocument> list(Pageable pageable) {
        return repository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public UserDocument updateDisplayName(String userId, String displayName) {
        UserDocument user = require(userId);
        return repository.save(
                user.withProfile(normalizeDisplayName(displayName), clock.instant()));
    }

    public void changePassword(String userId, String currentPassword,
                               String newPassword) {
        UserDocument user = require(userId);
        if (!passwordEncoder.matches(currentPassword == null ? "" : currentPassword,
                user.passwordHash())) {
            throw new PlatformException(ErrorCode.UNAUTHORIZED,
                    "The current password is incorrect.");
        }
        validatePassword(newPassword);
        repository.save(user.withPassword(passwordEncoder.encode(newPassword),
                clock.instant()));
    }

    public UserDocument updateRoles(String userId, Set<Role> roles) {
        UserDocument user = require(userId);
        Set<Role> normalised = normaliseRoles(roles);
        if (user.admin() && !normalised.contains(Role.ADMIN)
                && repository.countByRolesContainingAndStatus(Role.ADMIN,
                        UserStatus.ACTIVE) <= 1) {
            throw new PlatformException(ErrorCode.CONFLICT,
                    "The last active administrator cannot lose the ADMIN role.");
        }
        return repository.save(user.withRoles(normalised, clock.instant()));
    }

    public UserDocument updateStatus(String userId, UserStatus status) {
        Objects.requireNonNull(status, "status must not be null");
        UserDocument user = require(userId);
        if (user.admin() && status == UserStatus.DISABLED
                && repository.countByRolesContainingAndStatus(Role.ADMIN,
                        UserStatus.ACTIVE) <= 1) {
            throw new PlatformException(ErrorCode.CONFLICT,
                    "The last active administrator cannot be disabled.");
        }
        return repository.save(user.withStatus(status, clock.instant()));
    }

    public boolean adminExists(String email) {
        return repository.findByEmail(normalizeEmail(email))
                .map(UserDocument::admin)
                .orElse(false);
    }

    public long count() {
        return repository.count();
    }

    public static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_FAILED,
                    "Email must not be blank.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_FAILED,
                    "Display name must not be blank.");
        }
        String trimmed = displayName.trim();
        if (trimmed.length() > MAXIMUM_DISPLAY_NAME_LENGTH) {
            throw new PlatformException(ErrorCode.VALIDATION_FAILED,
                    "Display name must not exceed " + MAXIMUM_DISPLAY_NAME_LENGTH
                            + " characters.");
        }
        return trimmed;
    }

    private static void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MINIMUM_PASSWORD_LENGTH) {
            throw new PlatformException(ErrorCode.VALIDATION_FAILED,
                    "Password must be at least " + MINIMUM_PASSWORD_LENGTH
                            + " characters long.");
        }
        if (rawPassword.length() > MAXIMUM_PASSWORD_LENGTH) {
            throw new PlatformException(ErrorCode.VALIDATION_FAILED,
                    "Password must not exceed " + MAXIMUM_PASSWORD_LENGTH
                            + " characters.");
        }
    }

    private static Set<Role> normaliseRoles(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of(Role.USER);
        }
        return Set.copyOf(EnumSet.copyOf(roles));
    }

    private static PlatformException invalidCredentials() {
        return new PlatformException(ErrorCode.UNAUTHORIZED,
                "Email or password is incorrect.");
    }
}
