package dev.derops.registry.auth;

import dev.derops.registry.config.AppProperties;
import dev.derops.registry.user.AppUser;
import dev.derops.registry.user.RoleRepository;
import dev.derops.registry.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first administrator on an empty database from
 * BOOTSTRAP_ADMIN_PASSWORD, so no default credentials ship with the application.
 */
@Component
public class BootstrapAdmin implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public BootstrapAdmin(UserRepository users, RoleRepository roles, PasswordEncoder encoder, AppProperties props) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        String password = props.bootstrap().adminPassword();
        if (password == null || password.isBlank()) {
            log.warn("No users exist and BOOTSTRAP_ADMIN_PASSWORD is not set: nobody can log in yet");
            return;
        }
        AppUser admin = new AppUser(props.bootstrap().adminUsername(), "Administrator");
        admin.setPasswordHash(encoder.encode(password));
        admin.getRoles().add(roles.findByCode("ADMIN").orElseThrow());
        users.save(admin);
        log.info("Created initial administrator '{}'", admin.getUsername());
    }
}
