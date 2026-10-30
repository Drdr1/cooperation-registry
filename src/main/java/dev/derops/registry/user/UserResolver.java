package dev.derops.registry.user;

import dev.derops.registry.common.ApiException;
import org.springframework.stereotype.Component;

/** Validates user ids coming from requests (e.g. responsible person). */
@Component
public class UserResolver {

    private final UserRepository users;

    public UserResolver(UserRepository users) {
        this.users = users;
    }

    public AppUser activeOrNull(Long id) {
        return id == null ? null : active(id);
    }

    public AppUser active(Long id) {
        AppUser user = users.findById(id).orElseThrow(() -> ApiException.badRequest("error.unknownUser", id));
        if (!user.isActive()) {
            throw ApiException.badRequest("error.inactiveUser", id);
        }
        return user;
    }
}
