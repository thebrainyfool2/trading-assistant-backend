package com.tradingassistant.platform.user;

import com.tradingassistant.platform.security.Role;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * MongoDB representation of a platform account.
 *
 * <p>Only the BCrypt hash is stored; the raw password never reaches the
 * database, a log or an API response. Instances are immutable and every change
 * produces a new document.</p>
 *
 * @param id           document identity
 * @param email        normalised lower-case email, unique
 * @param displayName  name shown in the client
 * @param passwordHash BCrypt hash
 * @param roles        granted roles
 * @param status       account state
 * @param createdAt    registration instant
 * @param updatedAt    last profile or role change
 * @param lastLoginAt  last successful authentication, when known
 */
@Document(collection = "users")
public record UserDocument(
        @Id String id,
        @Indexed(unique = true) String email,
        String displayName,
        String passwordHash,
        Set<Role> roles,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant lastLoginAt) {

    public UserDocument {
        email = Objects.requireNonNull(email, "email must not be null");
        displayName = Objects.requireNonNull(displayName, "displayName must not be null");
        passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        roles = roles == null || roles.isEmpty()
                ? Set.of(Role.USER) : Set.copyOf(EnumSet.copyOf(roles));
        status = status == null ? UserStatus.ACTIVE : status;
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public static UserDocument create(String email, String displayName, String passwordHash,
                                      Set<Role> roles, Instant at) {
        return new UserDocument(null, email, displayName, passwordHash, roles,
                UserStatus.ACTIVE, at, at, null);
    }

    public UserDocument withProfile(String newDisplayName, Instant at) {
        return new UserDocument(id, email, newDisplayName, passwordHash, roles, status,
                createdAt, at, lastLoginAt);
    }

    public UserDocument withPassword(String newPasswordHash, Instant at) {
        return new UserDocument(id, email, displayName, newPasswordHash, roles, status,
                createdAt, at, lastLoginAt);
    }

    public UserDocument withRoles(Set<Role> newRoles, Instant at) {
        return new UserDocument(id, email, displayName, passwordHash, newRoles, status,
                createdAt, at, lastLoginAt);
    }

    public UserDocument withStatus(UserStatus newStatus, Instant at) {
        return new UserDocument(id, email, displayName, passwordHash, roles, newStatus,
                createdAt, at, lastLoginAt);
    }

    public UserDocument withLogin(Instant at) {
        return new UserDocument(id, email, displayName, passwordHash, roles, status,
                createdAt, at, at);
    }

    public boolean active() {
        return status == UserStatus.ACTIVE;
    }

    public boolean admin() {
        return roles.contains(Role.ADMIN);
    }
}
