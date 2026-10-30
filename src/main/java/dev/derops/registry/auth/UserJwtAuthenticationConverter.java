package dev.derops.registry.auth;

import dev.derops.registry.user.AppUser;
import dev.derops.registry.user.UserRepository;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Turns a valid JWT into an authentication whose authorities are the user's
 * <em>current</em> permissions from the database. A deactivated user or a role
 * change takes effect on the next request, not when the token expires.
 */
@Component
public class UserJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository users;

    public UserJwtAuthenticationConverter(UserRepository users) {
        this.users = users;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        AppUser user = users.findWithRolesByUsername(jwt.getSubject())
                .orElseThrow(() -> new DisabledException("Unknown user"));
        if (!user.isActive()) {
            throw new DisabledException("User is deactivated");
        }
        List<GrantedAuthority> authorities = user.effectivePermissions().stream()
                .<GrantedAuthority>map(p -> new SimpleGrantedAuthority(p.name()))
                .toList();
        return new JwtAuthenticationToken(jwt, authorities, user.getUsername());
    }
}
