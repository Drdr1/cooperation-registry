package dev.derops.registry.auth;

import dev.derops.registry.user.AppUser;
import dev.derops.registry.user.Role;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record OneIdCallbackRequest(@NotBlank String code, @NotBlank String state) {}

    public record OneIdAuthorizeResponse(String url, String state) {}

    public record UserInfo(Long id, String username, String fullName, String preferredLang,
                           List<String> roles, List<String> permissions) {
        public static UserInfo of(AppUser u) {
            return new UserInfo(u.getId(), u.getUsername(), u.getFullName(), u.getPreferredLang(),
                    u.getRoles().stream().map(Role::getCode).sorted().toList(),
                    u.effectivePermissions().stream().map(Enum::name).sorted().toList());
        }
    }

    public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, UserInfo user) {}
}
