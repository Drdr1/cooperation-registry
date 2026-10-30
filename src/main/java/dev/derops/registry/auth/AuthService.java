package dev.derops.registry.auth;

import dev.derops.registry.audit.AuditAction;
import dev.derops.registry.audit.AuditService;
import dev.derops.registry.auth.AuthDtos.LoginResponse;
import dev.derops.registry.auth.AuthDtos.UserInfo;
import dev.derops.registry.common.ApiException;
import dev.derops.registry.config.AppProperties;
import dev.derops.registry.user.AppUser;
import dev.derops.registry.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    static final String ENTITY = "USER";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final OneIdClient oneId;
    private final AuditService audit;
    private final AppProperties props;
    private final Clock clock;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens,
                       OneIdClient oneId, AuditService audit, AppProperties props, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.oneId = oneId;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Password login with lockout after repeated failures. Failed attempts and their
     * audit records are committed even though the request fails ({@code noRollbackFor}).
     */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse login(String username, String password) {
        Instant now = clock.instant();
        AppUser user = users.findWithRolesByUsername(username).orElse(null);
        if (user == null) {
            failed(username, null, "unknown_user");
            throw badCredentials();
        }
        if (!user.isActive()) {
            failed(username, user.getId(), "inactive");
            throw badCredentials();
        }
        if (user.isLocked(now)) {
            failed(username, user.getId(), "locked");
            throw new ApiException(HttpStatus.LOCKED, "error.accountLocked");
        }
        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            int attempts = user.getFailedLogins() + 1;
            if (attempts >= props.security().maxFailedLogins()) {
                user.setLockedUntil(now.plus(props.security().lockout()));
                user.setFailedLogins(0);
            } else {
                user.setFailedLogins(attempts);
            }
            failed(username, user.getId(), "bad_password");
            throw badCredentials();
        }
        return succeed(user, "password", now);
    }

    public AuthDtos.OneIdAuthorizeResponse oneIdAuthorize() {
        String state = tokens.issueOneIdState();
        return new AuthDtos.OneIdAuthorizeResponse(oneId.authorizationUrl(state), state);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse oneIdLogin(String code, String state) {
        if (!tokens.isValidOneIdState(state)) {
            throw ApiException.badRequest("error.oneidState");
        }
        OneIdClient.OneIdProfile profile;
        try {
            profile = oneId.exchange(code);
        } catch (OneIdClient.OneIdException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "error.oneidUnavailable");
        }
        AppUser user = users.findWithRolesByOneidSubject(profile.subject()).orElse(null);
        if (user == null || !user.isActive()) {
            // Accounts are provisioned by an administrator; SSO never creates users on its own.
            audit.recordAs(AuditService.SYSTEM_USER, AuditAction.LOGIN_FAILED, ENTITY, user == null ? null : user.getId(),
                    Map.of("method", "oneid", "reason", user == null ? "not_linked" : "inactive", "subject", profile.subject()));
            throw ApiException.forbidden("error.oneidNotLinked");
        }
        return succeed(user, "oneid", clock.instant());
    }

    @Transactional(readOnly = true)
    public UserInfo me(String username) {
        return users.findWithRolesByUsername(username)
                .map(UserInfo::of)
                .orElseThrow(() -> ApiException.notFound("user", username));
    }

    private LoginResponse succeed(AppUser user, String method, Instant now) {
        user.setFailedLogins(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        audit.recordAs(user.getUsername(), AuditAction.LOGIN_SUCCESS, ENTITY, user.getId(), Map.of("method", method));
        TokenService.IssuedToken token = tokens.issueAccessToken(user);
        return new LoginResponse(token.value(), "Bearer", token.expiresAt(), UserInfo.of(user));
    }

    private void failed(String username, Long userId, String reason) {
        String name = username.length() > 64 ? username.substring(0, 64) : username;
        audit.recordAs(name, AuditAction.LOGIN_FAILED, ENTITY, userId, Map.of("method", "password", "reason", reason));
    }

    private static ApiException badCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "error.badCredentials");
    }
}
