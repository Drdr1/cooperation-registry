package dev.derops.registry.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class UserDtos {

    private UserDtos() {}

    /** Compact user reference embedded in other DTOs (responsible person, etc.). */
    public record UserRef(Long id, String username, String fullName) {
        public static UserRef of(AppUser u) {
            return u == null ? null : new UserRef(u.getId(), u.getUsername(), u.getFullName());
        }
    }

    public record UserDto(Long id, String username, String fullName, String email, String department,
                          String oneidSubject, String preferredLang, boolean active, List<String> roles,
                          Instant lastLoginAt, Instant lockedUntil, Long version) {
        public static UserDto of(AppUser u) {
            return new UserDto(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(), u.getDepartment(),
                    u.getOneidSubject(), u.getPreferredLang(), u.isActive(),
                    u.getRoles().stream().map(Role::getCode).sorted().toList(),
                    u.getLastLoginAt(), u.getLockedUntil(), u.getVersion());
        }
    }

    public record CreateUserRequest(
            @NotBlank @Pattern(regexp = "^[a-z0-9._-]{3,64}$") String username,
            @NotBlank @Size(max = 255) String fullName,
            @Email @Size(max = 255) String email,
            @Size(max = 255) String department,
            @Size(min = 10, max = 128) String password,
            @Size(max = 128) String oneidSubject,
            @Pattern(regexp = "^(uz|ru|en)$") String preferredLang,
            @NotEmpty Set<String> roles) {}

    public record UpdateUserRequest(
            @NotBlank @Size(max = 255) String fullName,
            @Email @Size(max = 255) String email,
            @Size(max = 255) String department,
            @Size(max = 128) String oneidSubject,
            @Pattern(regexp = "^(uz|ru|en)$") String preferredLang,
            @NotNull Boolean active,
            @NotEmpty Set<String> roles,
            @NotNull Long version) {}

    public record ResetPasswordRequest(@NotBlank @Size(min = 10, max = 128) String newPassword) {}

    public record RoleDto(Long id, String code, String name, boolean systemRole, List<Permission> permissions) {
        public static RoleDto of(Role r) {
            return new RoleDto(r.getId(), r.getCode(), r.getName(), r.isSystemRole(),
                    r.getPermissions().stream().sorted().toList());
        }
    }

    public record RoleRequest(
            @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$") String code,
            @NotBlank @Size(max = 255) String name,
            @NotNull Set<Permission> permissions) {}
}
