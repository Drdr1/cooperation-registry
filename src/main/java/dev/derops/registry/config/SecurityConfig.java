package dev.derops.registry.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import dev.derops.registry.auth.UserJwtAuthenticationConverter;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    public static final String TOKEN_USE_CLAIM = "token_use";
    public static final String TOKEN_USE_ACCESS = "access";

    /** SOAP endpoint for external government systems: HTTP Basic with a dedicated integration account. */
    @Bean
    @Order(1)
    @SuppressWarnings("deprecation")
    SecurityFilterChain soapChain(HttpSecurity http, AppProperties props, PasswordEncoder encoder) throws Exception {
        InMemoryUserDetailsManager clients = new InMemoryUserDetailsManager();
        String password = props.soap().password();
        if (password != null && !password.isBlank()) {
            clients.createUser(User.withUsername(props.soap().username())
                    .password(encoder.encode(password))
                    .authorities("SOAP_CLIENT")
                    .build());
        } else {
            log.warn("app.soap.password is not set: the SOAP endpoint will reject every request");
        }
        // Full-path matcher: /ws is served by Spring-WS's own servlet, which an MVC-based
        // matcher would not see, letting the request fall through to the JWT chain.
        http.securityMatcher(new AntPathRequestMatcher("/ws/**"))
                .authorizeHttpRequests(a -> a.anyRequest().hasAuthority("SOAP_CLIENT"))
                .httpBasic(Customizer.withDefaults())
                .authenticationManager(soapAuthenticationManager(clients, encoder))
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    /** REST API: stateless JWT bearer tokens; permissions are re-read from the database on every request. */
    @Bean
    @Order(2)
    SecurityFilterChain apiChain(HttpSecurity http, UserJwtAuthenticationConverter converter) throws Exception {
        http.authorizeHttpRequests(a -> a
                        .requestMatchers("/api/auth/login", "/api/auth/oneid/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter)))
                .csrf(c -> c.disable()) // bearer tokens, no cookies: CSRF does not apply
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    /** Dedicated manager so the SOAP chain never falls back to another user store or encoder. */
    @SuppressWarnings("deprecation")
    private static AuthenticationManager soapAuthenticationManager(InMemoryUserDetailsManager clients, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(clients);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtEncoder jwtEncoder(AppProperties props) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey(props)));
    }

    @Bean
    JwtDecoder jwtDecoder(AppProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey(props))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(props.jwt().issuer()),
                // Rejects other signed tokens we issue (e.g. OneID login state) when presented as access tokens.
                new JwtClaimValidator<String>(TOKEN_USE_CLAIM, TOKEN_USE_ACCESS::equals));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    public static SecretKey signingKey(AppProperties props) {
        byte[] bytes = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes");
        }
        if (props.jwt().secret().startsWith("change-me")) {
            log.warn("Using the default JWT secret. Set JWT_SECRET before deploying.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
