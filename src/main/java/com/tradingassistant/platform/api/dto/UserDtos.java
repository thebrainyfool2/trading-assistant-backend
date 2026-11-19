package com.tradingassistant.platform.api.dto;

import com.tradingassistant.platform.security.Role;
import com.tradingassistant.platform.user.UserDocument;
import com.tradingassistant.platform.user.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Account API contract.
 */
public final class UserDtos {

    private UserDtos() {
    }

    public record UserView(
            String id,
            String email,
            String displayName,
            List<String> roles,
            UserStatus status,
            Instant createdAt,
            Instant lastLoginAt) {

        public static UserView from(UserDocument user) {
            return new UserView(user.id(), user.email(), user.displayName(),
                    user.roles().stream().map(Enum::name).sorted().toList(),
                    user.status(), user.createdAt(), user.lastLoginAt());
        }
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 80) String displayName) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 128) String newPassword) {
    }

    public record UpdateRolesRequest(@NotEmpty Set<Role> roles) {
    }

    public record UpdateStatusRequest(@NotNull UserStatus status) {
    }

    public record SessionCountView(long activeRefreshTokens) {
    }
}
