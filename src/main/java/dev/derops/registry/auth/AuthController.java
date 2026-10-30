package dev.derops.registry.auth;

import dev.derops.registry.auth.AuthDtos.LoginRequest;
import dev.derops.registry.auth.AuthDtos.LoginResponse;
import dev.derops.registry.auth.AuthDtos.OneIdAuthorizeResponse;
import dev.derops.registry.auth.AuthDtos.OneIdCallbackRequest;
import dev.derops.registry.auth.AuthDtos.UserInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    @Operation(summary = "Username/password login", description = "Locks the account for a while after repeated failures.")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request.username(), request.password());
    }

    @GetMapping("/oneid/authorize")
    @Operation(summary = "Start OneID SSO", description = "Returns the URL to redirect the browser to and a signed state.")
    public OneIdAuthorizeResponse oneIdAuthorize() {
        return auth.oneIdAuthorize();
    }

    @PostMapping("/oneid/callback")
    @Operation(summary = "Finish OneID SSO", description = "Exchanges the authorization code; the OneID identity must be linked to an account.")
    public LoginResponse oneIdCallback(@Valid @RequestBody OneIdCallbackRequest request) {
        return auth.oneIdLogin(request.code(), request.state());
    }

    @GetMapping("/me")
    @Operation(summary = "Current user, roles and effective permissions")
    public UserInfo me(Authentication authentication) {
        return auth.me(authentication.getName());
    }
}
