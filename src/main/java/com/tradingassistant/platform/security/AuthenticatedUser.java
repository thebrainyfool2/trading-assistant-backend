package com.tradingassistant.platform.security;

import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Principal reconstructed from a valid access token.
 *
 * <p>It carries only the identity and roles already present in the signed token;
 * it never carries credentials. Controllers receive it through
 * {@code @AuthenticationPrincipal} instead of parsing the token themselves.</p>
 *
 * @param userId user document identity
 * @param email  account email
 * @param roles  granted roles
 */
public record AuthenticatedUser(String userId, String email, Set<Role> roles)
        implements Principal {

    public AuthenticatedUser {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    @Override
    public String getName() {
        return userId;
    }

    public boolean admin() {
        return roles.contains(Role.ADMIN);
    }

    public List<String> authorities() {
        return roles.stream().map(Role::authority).sorted().toList();
    }

    public static AuthenticatedUser of(String userId, String email, List<String> roles) {
        Objects.requireNonNull(roles, "roles must not be null");
        Set<Role> parsed = roles.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Role::valueOf)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new AuthenticatedUser(userId, email, parsed);
    }
}
